# Chat Quality Evals

Heartline uses the small local model as a rewriter, not as the whole chat brain.
The app must pass deterministic response-director evals before a model swap is useful.

## What The Evals Check

- Directly answers the latest user message.
- Uses preset response seeds as guidance instead of inventing a new topic.
- Includes persona day-life when the user asks about the companion's day.
- Uses recent chat context for follow-up questions like "what did you say you were doing?"
- Keeps replies to 1-3 short mobile bubbles.
- Rejects protocol leaks, raw JSON, quote-back replies, and generic clarification loops.

## Current Test Coverage

- `ConversationDirectorTest`: intent detection, fallback validation, JSON leak prevention, repetition handling, memory candidates.
- `ChatQualityEvalTest`: deterministic multi-scenario quality matrix across Lara, Maya, Amina, and Nia.
- `BundledLlmQualityInstrumentedTest`: arm64-only real bundled-model rewrite eval for the Lara follow-up context failure.

## Model Decision

Current target: `litert-community/SmolLM2-360M-Instruct`.

Reason:
- It is already packaged for Android LiteRT-LM.
- It stays under the 0.8B limit.
- It is the next SmolLM2 size above 135M, which gives the rewriter more language capacity without changing architecture.

Rejected for now:
- Qwen3-0.6B: under 0.8B, but needs stricter thinking-mode controls and is a larger architecture migration for this app.
- Qwen2.5-0.5B-Instruct: under 0.8B and available in LiteRT form, but the app already has a SmolLM2 LiteRT pipeline and the next controlled comparison should be 135M vs 360M first.

## Next Comparison

Run the arm64 instrumented eval on a real Android device for:

1. SmolLM2-135M-Instruct
2. SmolLM2-360M-Instruct
3. Qwen2.5-0.5B-Instruct LiteRT

Keep the model only if it improves the eval matrix without increasing no-response timeouts or generic fallback rates.
