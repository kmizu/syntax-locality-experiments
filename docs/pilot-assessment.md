# Pilot assessment before main freeze

Status: **full P1 pilot complete; blind assessment recorded at `2026-10-05T17:53:33.3470597Z`**. The rule below was recorded on 2026-10-05 UTC before reviewing full-pilot directions. Neither harder-reading trigger fired, so the committed rule retains the original main grid. Main still requires the reviewed source, matching new-source capabilities, and frozen artifacts before dispatch. Live execution is already authorized.

Authoritative specification: [sections 7.1–7.6](../scala3-syntax-locality-icl-experiment.md). Original main is T1 / after_close with depth `[2,4,8,16]`, filler `[0,32,128,512]`, 32 families per cell, both lexical regimes, five styles: **512 families / 5,120 requests**. This is an initial design, not an established power optimum.

## Verified operational evidence

Saved [preflight capabilities](../runs/preflight-terra/capabilities.json), checked `2026-10-05T15:41:48.113481Z`, verify `gpt-5.6-terra`, reasoning `low`, reading output cap 8,192, successful input counting and generation, two HTTP attempts, 29 known generation tokens, and zero unresolved reservation tokens. This tiny probe is not a model-performance estimate.

Saved [P0 live smoke summary](../runs/smoke-terra/summary.csv) and [report](../runs/smoke-terra/report.md) verify the following operational result; `synthetic_mock=false` in [execution mode](../runs/smoke-terra/execution-mode.json):

| Planned | Dispatched | Terminal | Model-evaluable | Correct | Infrastructure missing | Unexecuted | Incomplete/refusal |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 20 | 20 | 20 | 20 | 16 | 0 | 0 | 0 / 0 |

Known final-trial and generation-attempt usage are both **35,472 tokens**; 40 HTTP attempts comprise 20 input counts plus 20 generations; no generation retries were recorded. The smoke protocol is `4abffafa113f24c288d9de9269fe69cdaf9734002fb9990a5e434aa620b0f72e`. Smoke's four audited families use natural vocabulary only. Its 80% pooled accuracy is an operational observation, not a comparative hypothesis result or a basis for changing the main grid.

## Full pilot requirements and completed evidence

The P1 pilot must contain all **1,800** planned generations with the same model/effort, fixed eight examples, and common task-specific output caps across A–E:

| Task | Structural families | Depth | Filler | Views | Expected requests |
| --- | ---: | --- | --- | --- | ---: |
| T1 scope lookup | 36 reading families | 2, 4, 8 | 0, 32, 128 | before + after | 720 |
| T2 active stack | Same 36 reading families | 2, 4, 8 | 0, 32, 128 | before + after | 720 |
| T3 AST-to-source | Separate 36 generation families | 2, 4, 8 | 0, 8, 32 | complete | 360 |

All rows include natural + nonce, five styles, and one replicate. Save the actual run path/protocol hash, date range, requested/returned model, planned/dispatched/terminal/evaluable/correct counts, infrastructure/unexecuted/incomplete/refusal counts, token-accounting completeness, retry/latency measures, and anonymous condition/task/grid breakdowns. Audit exact cardinalities and the complete matched blocks; a partial run cannot be presented as the full pilot.

The original live job completed successfully before assessment. Saved artifacts are in [runs/verified-pilot-p1](../runs/verified-pilot-p1/manifest.json), with protocol `f7dd15dd3c44b507506e123517157926a35a53b52c1d4aab278664f512a1e182`, source `f3e5a0a43c3ca7332be53f098c2b8d9064360312d6e7d5efc16cc4a74fd646ab`, dataset `ed5a26acb0f00345ae164a4abcb4c020484ceb241cbd5d1511658910bad0bb61`, and `synthetic_mock=false`. Trial timestamps span `2026-10-05T15:45:49.493Z`–`2026-10-05T17:51:25.914Z`; the final report was written at `2026-10-05T17:51:53.0651994Z`. Requested and returned model were `gpt-5.6-terra`, reasoning `low`, with common reading/generation caps 8,192/16,384.

The [blind-only verification output](../verification/blind-pilot-v1-assessment.json) independently joins cases to scores, checks all 1,800 unique planned/scored/terminal IDs, verifies the original dataset/schedule hashes, and cross-checks exact Scala summary counts and the primary pair's symmetric family counts. It exposes no directional contrasts, separate primary-condition rates, effect sizes, or CIs. The assessment was performed only after the original live worker and its final score/report commands exited successfully.

| Planned | Dispatched | Terminal | Model-evaluable | Correct | Infrastructure missing | Unexecuted | Incomplete/refusal |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,800 | 1,800 | 1,800 | 1,800 | 1,622 | 0 | 0 | 0 / 0 |

Pooled accuracy is **90.111%**. All 1,800 provider outcomes have status `completed`. Known final-trial and all-attempt generation usage are both **6,148,886 tokens**, comprising 5,921,140 input and 227,746 output tokens; 106,161 reasoning tokens are a subset of output. Usage is known for every trial. There were 1,800 generations and 3,600 HTTP attempts (one input count and one generation per trial), **zero generation retries and zero unresolved outcomes**. Mean recorded generation latency was **11,793.694 ms**. Earlier snapshot unresolved entries were concurrent-copy observations and are not present in the completed run.

| Task / view | Evaluable | Correct | Pooled accuracy | Retained failures |
| --- | ---: | ---: | ---: | --- |
| T1 / before_close | 360 | 360 | 100% | 0 |
| T1 / after_close | 360 | 306 | 85% | 54 wrong answers |
| T2 / before_close | 360 | 357 | 99.167% | 3 wrong answers |
| T2 / after_close | 360 | 315 | 87.5% | 45 wrong answers |
| T3 / complete | 360 | 284 | 78.889% | 73 invalid generated syntaxes; 3 valid-syntax/wrong-AST |

There were zero invalid answer formats. The 73 T3 first parser codes are `unclosed_scope` (69), `close_mismatch` (3), and `invalid_nop` (1), graded by the original v1 parser. Each corresponding response has status `completed` and one generation attempt, with 151–764 output tokens. Across all responses, output ranges were T1 6–826, T2 11–235, and T3 82–918, below their common caps. No provider-level cap truncation or unresolved operational missingness is observed; no task-cap increase is indicated by these artifacts. These model failures remain in the pilot denominator.

### Recorded blind reading verdict

T1 / after_close pooled accuracy is **306/360 = 85%**. The minimum and maximum over anonymous styles are **72.222%** and **91.667%**, so Trigger 1 (every style at least 98%) is **false**. The prespecified primary pair's combined accuracy is **126/144 = 87.5%**. Both primary conditions in both lexical regimes are evaluable for **36/36 structural families (100%)**, and **13** covered families disagree in at least one lexical regime. Independent case/score reconstruction agrees with the saved Scala counts, including the rule that lexical disagreements cannot cancel out of the discordant-family count. Trigger 2 is **false**: coverage passes, while pooled accuracy is below 95% and discordance exceeds one.

| Depth | Filler 0 | Filler 32 | Filler 128 |
| ---: | ---: | ---: | ---: |
| 2 | 34/40 (85%) | 36/40 (90%) | 37/40 (92.5%) |
| 4 | 25/40 (62.5%) | 35/40 (87.5%) | 38/40 (95%) |
| 8 | 33/40 (82.5%) | 35/40 (87.5%) | 33/40 (82.5%) |

These are pooled depth/filler diagnostics, each with 40 planned and evaluable trials; they do not support a directional syntax effect or a monotonic difficulty trend. The committed rule therefore **does not trigger a harder reading pilot** and retains main T1 / after_close depth `[2,4,8,16]`, filler `[0,32,128,512]`, 32 families per cell: **512 families / 5,120 requests**, both lexical regimes and all five styles. The recorded decision uses no directional result. The historical request-capture deviation below remains explicit and does not pool pilot observations into main.

### Provisional operational snapshot

[Snapshot 001](../runs/pilot-snapshot-001/snapshot.json) was captured at `2026-10-05T15:54:14.2741116Z` from `runs/verified-pilot-p1` while its live worker was running. The saved [summary](../runs/pilot-snapshot-001/summary.csv) records **122/1,800 terminal/evaluable**, **112 strict correct**, 128 dispatched, and 1,672 unexecuted. Six dispatched outcomes are unresolved in this concurrent snapshot; that classification does **not** establish six API failures. Snapshot/copyscan timing can lag the live worker.

The blind operational review found **zero incomplete responses, zero refusals, zero invalid answer formats, and no generation retries** in the snapshot. Its ten evaluated failures comprise nine wrong answers (four T2 after-close, five T1 after-close) and one T3 first parser error `unclosed_scope`. That T3 response was provider-status `completed` with 548 output tokens, so it does not constitute an observed provider token-cap truncation. There is presently no cap-raising diagnosis from these partial data. Known generation usage is 376,988 tokens; no hypothesis conclusion or difficulty-trigger decision follows from this early, uneven sample.

[Snapshot 002](../runs/pilot-snapshot-002/snapshot.json), captured `2026-10-05T16:15:30.4415515Z`, records **453/1,800 terminal/evaluable**, **414 correct (91.391%)**, 455 dispatched, two unresolved concurrent-snapshot outcomes, and 1,345 unexecuted; known final/attempt generation usage is **1,409,835 tokens** with 910 HTTP attempts and no generation retries. Blind outcome review again found zero incomplete/refusal/invalid-format responses. Evaluated failures are 27 wrong answers (16 T2 after-close, 11 T1 after-close), 11 T3 first parser errors `unclosed_scope`, and one valid-syntax/wrong-AST T3 response. All eleven syntax failures have provider-status `completed`, output 263–595 tokens, and one generation attempt; these are retained model failures, not erased by retry or reclassified as demonstrated cap truncation. Full-pilot assessment, directional interpretation, and difficulty/grid decisions remain pending.

[Snapshot 003](../runs/pilot-snapshot-003/snapshot.json), captured `2026-10-05T16:40:45.9476048Z`, records **792/1,800 terminal/evaluable**, **711 correct**, 799 dispatched, seven unresolved copy/in-flight outcomes, and 1,001 unexecuted. The 792 terminal files agree with the saved counts; the seven unresolved snapshot outcomes do **not** confirm seven live API failures. Known final/attempt usage is **2,662,074 tokens**, with 1,599 HTTP attempts and 799 generations; no generation retries, incomplete responses, refusals, or invalid formats were recorded. Evaluated failures comprise 49 wrong answers, 29 invalid generated syntaxes, and three valid-syntax/wrong-AST results. All 29 T3 parser failures first report `unclosed_scope`; their provider-status is `completed`, output is 183–595 tokens, and generation attempts are one each. No provider-level output-cap truncation is observed in this snapshot. Full-pilot interpretation and difficulty decisions remain pending; directional contrasts, per-style rates, and trigger inputs have not been reviewed.

[Snapshot 004](../runs/pilot-snapshot-004/snapshot.json), captured `2026-10-05T17:21:11.1073156Z`, records **1,354/1,800 terminal/evaluable**, **1,223 correct**, 1,360 dispatched, six unresolved copy/in-flight outcomes, and 440 unexecuted. Its 1,354 terminal files match the saved count; the six unresolved snapshot entries are **not confirmed live API failures**. Known final/attempt generation usage is **4,651,034 tokens**, with 2,724 HTTP attempts and 1,360 generations; no generation retries, incomplete responses, refusals, or invalid formats were recorded. Evaluated failures comprise 73 wrong answers, 55 invalid generated syntaxes, and three valid-syntax/wrong-AST results. All 55 T3 parser failures first report `unclosed_scope`; each response is provider-status `completed`, with 183–764 output tokens and one generation attempt. No output-cap truncation is observed. This remains a provisional **pilot-v1** snapshot with the request-provenance limitation recorded below; no version pooling, directional interpretation, trigger evaluation, or main-grid selection has been performed.

## Blind decision rule

Use completed **T1 / after_close** pilot data for reading difficulty. Remove syntax labels from the assessment view before reviewing it; do not use D−B, other directional contrasts, or favorable examples to select a grid. Check pooled accuracy, the minimum/maximum over anonymous styles, common outcome distributions, and depth × filler summaries. Keep T2/T3 results separate from this main-reading decision.

An optional one-step harder **reading-only** pilot is triggered if either:

1. Every anonymous style's model-evaluable T1 / after_close accuracy is at least **98%**; or
2. The **prespecified primary pair**, presented as two anonymous conditions with direction hidden, has pooled accuracy at least **95%**; at least 95% of planned reading families have both conditions in both lexical regimes evaluable; and **at most one** fully observed structural family has any correctness disagreement between that pair in either lexical regime.

The second rule operationalizes the specification's “almost no discordant pairs” without inspecting which syntax won. It uses the frozen primary pair rather than letting failures of an auxiliary baseline mask a primary-pair ceiling. The accuracy guard prevents a uniformly poor/floor-level result from prompting an unjustified harder task. These are practical pilot triggers, not a power calculation or proof of equivalence. Infrastructure missingness or output truncation must be diagnosed before interpreting a trigger.

If triggered, try the specification's candidate depth `[8,16,32]` and filler `[128,512,2048]` uniformly across styles using new seeds/namespace and a new protocol/run. Four families per cell, two lexical regimes, both views, and five styles give **720 reading-only requests**. Do not apply depth 16/32 to P1 T3: generation remains capped at depth 8 and filler 32. Audit depth-32 AST/parser resource limits and request lengths before live dispatch. Preserve and report every attempted grid; do not repeatedly tune until D wins.

If output is incomplete, revise that task's common cap for **all** styles before main freeze and record the new protocol. Do not give D/E a larger condition-specific cap. A prompt ambiguity requires a documented uniform fix and a fresh pilot/run; do not reinterpret old answers or combine changed protocols silently.

If the original reading pilot avoids the trigger and has no unresolved operational ambiguity, retain the original main grid unless a separate, recorded blind measurement problem warrants revision. If a harder reading pilot is performed, determine the final main grid from its aggregate ceiling/floor, coverage, and utilization diagnostics; record that grid and sample count explicitly **before** inspecting directional comparisons or creating main requests. Continued perfect reading is a valid result; a generation or different-effort effect does not become a reading effect.

### Exact artifact procedure for the completed pilot

1. After the live worker finishes, regenerate the saved Scala scores/report and preserve an immutable complete-run copy. Verify matching protocol/manifest hashes, `synthetic_mock=false`, exactly 1,800 unique planned/scored trial IDs, 1,800 dispatched/terminal, no unresolved outcomes, and task/view counts 360 each. Infrastructure missingness and truncation must be recorded and investigated before a difficulty decision.
2. From `summary.csv`, select only `task=scope_lookup`, `view=after_close`, `lexical_regime=ALL`, `depth=ALL`, `filler=ALL`, and non-ALL styles. There must be five style rows, each planned 72, totaling 360. Keep their names internal and output only anonymous rates/minimum/maximum and pooled totals. Compute each accuracy as integer `correct / model_evaluable`; pooled accuracy is `sum(correct) / sum(model_evaluable)`. Do not use rounded `strict_accuracy` for thresholds. Trigger 1 requires positive denominators and `100*correct >= 98*model_evaluable` in every anonymous row.
3. For Trigger 2, pool the two prespecified primary-style rows from that same selection, without displaying their separate rates or ordering. Require a positive denominator and `100*sum(correct) >= 95*sum(model_evaluable)`. From the primary-pair row of `paired-comparisons.csv`, read **only** `matched_families_available`, `matched_families_planned`, and `discordant_families`; require planned=36, `100*available >= 95*planned`, and discordant <=1. For 36 planned families, the coverage gate therefore requires at least 35. Do not display/read effect, separate baseline/treatment accuracies, relative error reduction, CIs, or directional failure examples before recording the grid decision.
4. For blind depth/filler diagnostics, use the non-ALL style rows with `task=scope_lookup`, `view=after_close`, explicit numeric depth/filler, and `lexical_regime=natural` or `nonce`. Pool their exact counts within each depth × filler cell. The nine cells each plan 40 trials. Never mix these rows with ALL rows or count the same trials twice.
5. Independently verify family coverage/discordance by joining `cases.jsonl.id` to `scores.jsonl.trial_id` one-to-one and selecting the same T1/after-close task/view. A covered family has both primary conditions model-evaluable in **both** regimes; it is discordant if their `strict_correct` booleans differ in **either** regime. Count each family once, including opposing lexical disagreements that cancel their mean. Suppress which condition was correct. This must match the two selected CSV family counts; disagreement blocks freeze pending investigation.

No numerical rule input is missing from the existing artifacts. `summary.csv` provides exact case counts/cells; `paired-comparisons.csv` provides exact covered/discordant family counts; cases+scores provide the auditable full matrix. A dedicated blind-only export is absent, so use a whitelist projection instead of opening the general report or contrast table. Existing Scala-generated counts suffice; this procedure requires no protocol/source change or extra API call.

## Before main freeze

### Pilot-v1 request provenance and source transition

The original live preflight, smoke, and completed P1 pilot saved complete canonical logical request bodies. Their v1 transport serialized object keys in insertion order, so the original submitted wire text was not captured byte-for-byte. Message content/order, model, effort, and caps were preserved; this is a historical request-capture deviation, not an omitted prompt or a reason to erase any outcome. Do not relabel reconstructed bodies as captured raw requests.

Pilot-v1 remains exploratory and separate from confirmatory main data. Preserve its original protocol/source and saved reports for the recorded blind difficulty assessment. Source commit `c4d3452` and its compiled runtime are archived under `verification/pilot-v1-source-c4d3452` and `verification/pilot-v1-runtime-c4d3452`; the archived CLI independently audited snapshot 003 against source hash `f3e5a0a43c3ca7332be53f098c2b8d9064360312d6e7d5efc16cc4a74fd646ab`.

The reviewed isolated fixes add exact shared request serialization, the separate preflight count body, distinct first close-kind/name diagnostics, and statement-order regression coverage. They preserve prompts/settings and strict acceptance. Integrate them only after the v1 live worker and its report commands finish. Then capture the required full sbt verification, perform a fresh new-source preflight, and create new plans before any further live run. The conditional harder-reading plan must be regenerated under the new source if the blind rule triggers. A complete replacement P1 pilot is not required solely for these serialization/diagnostic changes; no v1 and main observations will be pooled.

The full-pilot evidence and blind grid decision above are complete. The reviewed fixes were integrated as `fb024c2` and `219e7d9` after the original process finished. The required full `./scripts/sbt.ps1 test` exited zero at `2026-10-05T17:54:58Z` (28 core tests, 16 bench suites, 12 named preflight checks). The complete pilot copy's five report artifacts reproduced byte-identically with the archived v1 runtime, with zero HTTP; witness `verification/pilot-v1-completion.json`.

Fresh integrated-source capabilities in `runs/preflight-terra-v2/capabilities.json`, checked `2026-10-05T17:55:40.117919Z`, verify the same model/low effort/8,192 reading cap, exact shared request persistence, two successful HTTP attempts, 29 known tokens, and zero unresolved reservations. A fresh main plan and independent audit completed at `2026-10-05T17:58:04Z`: `runs/main-terra`, protocol `f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a`, source `1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1`. It retains the selected 512-family /5,120-request grid, one replicate, eight fixed examples and master/bootstrap seed `20261005`. Directional pilot comparisons remain unread. Freeze and actual main execution/reporting are still pending.

Only then create a new main plan and freeze its protocol, dataset, requests, scoring/contrasts, seed, source provenance, and stopping rules. Keep pilot/follow-up observations out of confirmatory main data. No stopping rule may depend on a favorable effect or CI. Preserve the actual model alias/returned model and dates rather than claiming an unavailable pinned snapshot.
