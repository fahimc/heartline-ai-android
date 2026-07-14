# Chat Quality Evals

Heartline does not let a sub-1B model decide the subject of a reply. The runtime
path is:

1. Detect the latest user intent.
2. Combine that intent with the persona routine, relationship state, memories,
   recent messages, and conversation summary.
3. Select a context-grounded reply plan from the response bank.
4. Ask the bundled model to rewrite that plan in the persona voice.
5. Reject the rewrite if it changes topic, speaker, facts, or length.
6. Save the validated bubbles and any memory candidates.

This split matters because every tested model below one billion parameters can
lose a conversational reference or invent a fact when asked to plan and write a
reply in one pass.

## Regression And Stress Coverage

- `ConversationDirectorTest` covers intent precedence, context recovery,
  speaker inversion, malformed model output, repetition, and memory extraction.
- `ChatQualityEvalTest` includes the reported dinner, presence, clarification,
  availability, work, fatigue, correction, and repeated-activity regressions.
- Its adversarial matrix runs 8 personas x 8 scenarios x 10 hostile model
  outputs: 640 validated replies. A reply fails if it drops the required topic,
  accepts a generic answer, leaks model protocol, or invents the wrong speaker.
- Screenshot regressions cover prompt metadata emitted as messages, including
  persona-style descriptors, relationship-stage text, output headings, and
  numbered bubble instructions. Every generated bubble must independently
  overlap the grounded reply plan or an intent-specific signal.
- `BundledLlmQualityInstrumentedTest` exercises the real bundled LiteRT model on
  arm64 Android. It is skipped on x86 emulators because LiteRT-LM inference in
  this app is arm64-only.

Run the deterministic suite with:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Sub-1B Model Evaluation

The comparison was run on 2026-07-13 and 2026-07-14 with official Transformers
checkpoints on Windows CPU. Ten short multi-turn scenarios tested corrections,
pronoun ownership, recent-chat references, persona day-life, memory follow-ups,
and topic continuity. A protocol label, invented event, reversed speaker, or
off-topic generic answer counted as a failure.

| Model | Parameters | Full-context score | Compact-rewriter score | Android result |
| --- | ---: | ---: | ---: | --- |
| SmolLM2-360M-Instruct | 360M | 8/10 nominal | 3/10 | Rejected: leaked labels and invented turns despite nominal matches |
| Qwen2.5-0.5B-Instruct | 500M | 6/10 | Not run | Rejected: lost corrections and memory follow-ups |
| Qwen3-0.6B | 600M | 6/10 | 5/10 | Selected: strongest compatible compact rewriter with an official LiteRT asset |
| LFM2-700M | 700M | 7/10 | Not run | Strong candidate, but no official LiteRT package; CPU average was about 4.63 s |
| Qwen3.5-0.8B | 800M | 7/10 | 1/10 | Rejected: poor constrained rewriting and roughly 1.1 GB LiteRT package |

The full-context SmolLM2 number is marked nominal because several outputs matched
a keyword while also exposing prompt labels or fabricating a turn. It was not a
usable 8/10 conversation result.

The reproducible Hugging Face harness is `tools/evaluate_sub1b_models.py`. Model
downloads are intentionally not part of Gradle or CI:

```powershell
python tools/evaluate_sub1b_models.py --model Qwen/Qwen3-0.6B --mode rewrite
```

## Runtime Decision

The APK target is `litert-community/Qwen3-0.6B` mixed INT4:

- File: `qwen3_0_6b_mixed_int4.litertlm`
- Size: `497,664,000` bytes
- SHA-256: `b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9`
- Thinking is disabled with `/no_think`.
- The rewriter receives only the prepared reply, not raw conversation history.
- A semantic validator requires plan overlap and intent-specific topic signals.
- Timeout, busy-engine, malformed, repetitive, or off-topic output falls back to
  the grounded plan, so a model failure cannot become a blank chat response.

Qwen3-0.6B is not treated as a general conversational brain. It is the best fit
among the tested models for this Android runtime after deployment size,
availability of a verified LiteRT artifact, rewrite quality, and latency risk are
considered together.
