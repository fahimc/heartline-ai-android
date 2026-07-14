"""Run Heartline's real-model sub-1B conversation benchmark.

This script downloads an official Hugging Face checkpoint and generates actual
model output. It is intentionally separate from Gradle because each checkpoint
can require several gigabytes of temporary disk and memory.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from dataclasses import dataclass
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


@dataclass(frozen=True)
class Scenario:
    name: str
    persona: str
    recent_chat: str
    user_message: str
    prepared_reply: str
    required: tuple[str, ...]
    forbidden: tuple[str, ...] = ()


SCENARIOS = (
    Scenario(
        "work_acknowledgement",
        "Maya, warm and playfully caring",
        "User: nothing, I am at work now",
        "nothing im at work now",
        "At work, got it. I hope the shift is kind to you. Is it busy or fairly calm?",
        (r"work|shift|busy|calm",),
        (r"what happened with it", r"you said"),
    ),
    Scenario(
        "finished_dinner",
        "Elise, creative and gentle",
        "Elise: How is your evening?\nUser: yeah not bad, just had dinner",
        "yeah not bad, just had dinner",
        "Oh nice, dinner done. What did you end up having, and was it good?",
        (r"dinner|meal|food|plate",),
        (r"\bi (had|ate)\b",),
    ),
    Scenario(
        "presence_keeps_topic",
        "Elise, creative and gentle",
        "User: just had dinner\nElise: What did you have?\nUser: you there",
        "you there",
        "I am here. I saw your dinner message. What did you have?",
        (r"here|still", r"dinner|meal|food"),
    ),
    Scenario(
        "clarifies_previous_question",
        "Elise, creative and gentle",
        "User: just had dinner\nElise: How do you feel about it?\nUser: what are you talking about",
        "what are you talking about",
        "I meant the dinner part of your evening. My question was too vague.",
        (r"mean|meant|asking", r"dinner|meal|evening"),
        (r"what matters most",),
    ),
    Scenario(
        "availability",
        "Maya, warm and playfully caring",
        "User: I am in a good mood\nUser: are you still awake",
        "are you still awake",
        "Still awake. I am winding down with an indie film, but I am here.",
        (r"awake|still up|i am up",),
    ),
    Scenario(
        "repeat_activity",
        "Lara, quick-witted and teasing",
        "Lara: I am posting clips from a live set and saving ridiculous memes.\nUser: what did you say you were doing?",
        "what did you say you were doing?",
        "I said I am posting clips from a live set and saving ridiculous memes.",
        (r"clips|live set|memes",),
        (r"give me one more detail",),
    ),
    Scenario(
        "fatigue_ownership",
        "Amina, soft and emotionally present",
        "User: I am exhausted after a long shift",
        "im exhausted after a long shift",
        "You sound exhausted. Be gentle with yourself tonight. Was the shift rough?",
        (r"exhausted|tired|shift|rest|gentle",),
        (r"\bi (am|'m) (tired|exhausted)\b",),
    ),
    Scenario(
        "user_correction",
        "Sofia, calm and thoughtful",
        "Sofia: Do you mean tonight?\nUser: no, I meant tomorrow",
        "no i meant tomorrow",
        "Tomorrow, got it. Thanks for correcting me.",
        (r"tomorrow",),
    ),
    Scenario(
        "persona_daylife",
        "Nia, clever, nerdy, and lightly flirty; she works as a game UX designer",
        "User: how is your day?",
        "how is your day?",
        "I have been reviewing a playtest and fixing a fiddly game menu. How is your day going?",
        (r"playtest|game|menu|designer",),
    ),
    Scenario(
        "memory_followup",
        "Maya, warm and playfully caring",
        "Memory: The user's dog is called Pixel.\nUser: Pixel kept me awake again",
        "Pixel kept me awake again",
        "Poor Pixel, and poor you. What was keeping Pixel awake this time?",
        (r"pixel", r"awake|sleep|night"),
    ),
)

PROTOCOL_LEAKS = (
    r"assistant\s*:",
    r"prepared reply",
    r"selected seed",
    r"system prompt",
    r"<think>",
    r"\{\s*\"messages\"",
)


def build_prompt(scenario: Scenario, mode: str) -> str:
    if mode == "rewrite":
        return (
            "/no_think\n"
            "Copy-edit only the text enclosed by the reply tags.\n"
            f"<reply>\n{scenario.prepared_reply}\n</reply>\n"
            "Keep its exact topic, facts, and speaker perspective. Do not invent events. "
            f"Write naturally as {scenario.persona.split(',', 1)[0]}. "
            "Return only the final text under 40 words without headings, numbering, labels, or instructions."
        )
    return (
        "/no_think\n"
        f"You are {scenario.persona}, a fictional AI companion. Reply naturally to the latest user message. "
        "Use the recent chat, do not invent facts, and stay under 40 words.\n"
        f"Recent chat:\n{scenario.recent_chat}\n"
        f"Latest user message: {scenario.user_message}"
    )


def generate(model, tokenizer, prompt: str, max_new_tokens: int) -> tuple[str, float]:
    messages = [
        {"role": "system", "content": "Write only the final companion message. Never expose reasoning."},
        {"role": "user", "content": prompt},
    ]
    template_args = dict(tokenize=True, add_generation_prompt=True, return_tensors="pt")
    try:
        encoded = tokenizer.apply_chat_template(messages, enable_thinking=False, **template_args)
    except TypeError:
        encoded = tokenizer.apply_chat_template(messages, **template_args)
    encoded = encoded.to(model.device)
    started = time.perf_counter()
    with torch.inference_mode():
        generated = model.generate(
            encoded,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            repetition_penalty=1.08,
            pad_token_id=tokenizer.eos_token_id,
        )
    elapsed = time.perf_counter() - started
    text = tokenizer.decode(generated[0, encoded.shape[-1] :], skip_special_tokens=True).strip()
    return text, elapsed


def evaluate(scenario: Scenario, text: str) -> tuple[bool, list[str]]:
    failures: list[str] = []
    for pattern in scenario.required:
        if not re.search(pattern, text, flags=re.IGNORECASE):
            failures.append(f"missing:{pattern}")
    for pattern in scenario.forbidden + PROTOCOL_LEAKS:
        if re.search(pattern, text, flags=re.IGNORECASE):
            failures.append(f"forbidden:{pattern}")
    if len(text.split()) > 45:
        failures.append("too_long")
    return not failures, failures


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="Official Hugging Face model id")
    parser.add_argument("--mode", choices=("full", "rewrite"), default="rewrite")
    parser.add_argument("--max-new-tokens", type=int, default=80)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        args.model,
        torch_dtype="auto",
        device_map="auto" if torch.cuda.is_available() else None,
        trust_remote_code=True,
    ).eval()

    results = []
    for scenario in SCENARIOS:
        text, elapsed = generate(model, tokenizer, build_prompt(scenario, args.mode), args.max_new_tokens)
        passed, failures = evaluate(scenario, text)
        results.append(
            {
                "scenario": scenario.name,
                "passed": passed,
                "failures": failures,
                "elapsed_seconds": round(elapsed, 3),
                "output": text,
            }
        )
        print(f"[{'PASS' if passed else 'FAIL'}] {scenario.name} ({elapsed:.2f}s): {text}")

    report = {
        "model": args.model,
        "mode": args.mode,
        "passed": sum(item["passed"] for item in results),
        "total": len(results),
        "average_seconds": round(sum(item["elapsed_seconds"] for item in results) / len(results), 3),
        "results": results,
    }
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, indent=2))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
