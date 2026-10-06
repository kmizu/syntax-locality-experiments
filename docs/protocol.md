# Protocol and invariants

The authoritative design is [scala3-syntax-locality-icl-experiment.md](../scala3-syntax-locality-icl-experiment.md), especially sections 3–11. This summary does not authorize extra live requests or replace the specification.

## Paired treatments and tasks

The five styles are `braces` (A), `generic_end` (B), `typed_end` (C), `named_end` (D), and `padded_end` (E). Corresponding trials preserve the AST, names, values, semantic order, examples, and lexical mapping; only syntax changes. All scope categories introduce the same single-pass lexical-scope semantics. E's `junk z_aaaaaaaa` is fixed and carries no scope identity.

T1 `scope_lookup` answers one integer. The primary view is `after_close`; `before_close` is a separate control view. T2 `active_stack` answers open block names as an outer-to-inner JSON string array, excluding global. Both reading tasks use the same prefix ending at the probe; no future code appears. T3 `ast_to_source` receives a neutral parent/order table and generates a complete program. Its P1 AST bodies are capped at depth 8 and 32 filler statements.

The fixed natural and eight nonce lexicons are logged. Natural/nonce are equally weighted in pilot/main comparisons; smoke uses natural only. Nonce allocation is deterministic and balanced within cells when possible. Neither nonce substitution nor character-length matching removes tokenization or pretraining effects.

## Oracle and grading

The recursive AST oracle and explicit parsed-event-stack oracle are independent implementations. Dataset audit requires agreement and cross-style gold equality. Tests include 10,000 generated ASTs, round trips, renaming/value metamorphisms, prefix boundaries, and rejected malformed closing labels. Finite test success is not proof of all-input correctness.

Strict grading accepts no explanatory text or code fences. T1 trims whitespace and requires one whole decimal integer; T2 parses the whole answer as a string-only JSON array with exact order; T3 strictly parses the entire source and compares kind, names, order, bindings, values, and nop payload. One whole-answer fence may be stripped for a separately labeled auxiliary score. Incomplete output is always strict failure, even if its text contains the answer.

Strict T3 syntax validity requires a completed parseable response. Refusal/incomplete generation counts as failure in that model-evaluable denominator; infrastructure outcomes remain missing. Body-only auxiliary correctness does not change strict validity or exact AST accuracy.

Model-evaluable categories are `correct`, `wrong_answer`, `invalid_answer_format`, `invalid_generated_syntax`, `valid_syntax_wrong_ast`, `refusal`, and `incomplete_output`. Only `correct` has correctness 1. `api_rejected`, `transport_failure`, `ambiguous_outcome`, and `decode_failure` are infrastructure missing; `not_dispatched` is separately unexecuted.

## Estimand and uncertainty

Primary main estimand: T1 / after_close / D−B strict accuracy, in percentage points. Replicates are paired/averaged inside family and lexical regime; natural/nonce means have equal weight; depth × filler cells have equal weight. A primary family must have model-evaluable B/D pairs in both planned regimes. A/C/E failures alone do not discard B/D pairs. A wholly missing planned stratum leaves the overall estimate undefined.

Each structural family is one sampling cluster. Stratified bootstrap samples the available family count with replacement independently inside each depth × filler cell, preserving its lexical/style/replicate bundle. It computes family, stratum, and equal-stratum means in that order. Use 10,000 draws and saved seed 20261005; percentiles interpolate at `(n−1)*p`. Smoke and provisional partial runs have no inferential CI. Degenerate distributions are flagged, with discordant-family and ceiling/floor diagnostics; opposite lexical disagreements must not vanish from the discordance count.

D−E, D−C, C−B, A contrasts, other views/tasks, and pilot results are exploratory absent a separately frozen testing plan. Exact McNemar is descriptive and only applied to single-regime, unaveraged binary family pairs. Relative error reduction is NA at zero baseline error. Tokens/correct is NA at zero correct or incomplete required usage.

All reports show planned/dispatched/terminal/evaluable/correct counts, infrastructure/unexecuted/incomplete/refusal counts, and matched available/planned families. Conservative operational success is correct/dispatched; unresolved dispatched outcomes supply an upper bound. Missingness sensitivity assigns non-evaluable planned comparison outcomes against/in favor of D. Main infrastructure missingness above 1% requires investigation before definitive superiority claims.

## Accounting, provenance, and stops

Planning/audit/mock/scoring/reporting require no network. Live count/generation require `--execute` and numeric limits. Count, generation, and retries consume HTTP-attempt limits; only newly started logical generations consume generation-call limits. Reserve counted input + maximum output + 256 tokens before dispatch. Unknown outcomes retain reservations, including across restarts and UTC rollover. Record known attempt usage separately from unique final-trial usage; neither token estimates nor shared-model eligibility verifies billing/free balance.

Store request hashes and trial identities, every attempt, response IDs/status/returned model, usage and timing. Do not retry model errors or optimize prompts by condition. Do not cache away requested replicates, double-count resume results, or combine changed protocol versions without an explicit analysis.

Freeze binds protocol, implementation/build hashes, dataset/schedule/gold/request artifacts, matching successful preflight settings, and source provenance. Main requires the actual frozen hash and verification. Pilot assessment precedes main interpretation; no stop may depend on a favorable effect/CI. Prespecified count, budget/time caps, and infrastructure failures govern stopping.

Failure examples are chosen before inspecting narratives: D correct/B wrong, D wrong/B correct, and both wrong; sort deterministically by trial hash, at most three pairs per category. Include both inputs, raw answers, gold, classifications, and IDs. Long excerpts identify exact line ranges and link the full input.
