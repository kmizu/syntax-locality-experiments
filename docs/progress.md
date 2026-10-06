# Implementation ledger

Plan and specification: ../scala3-syntax-locality-icl-experiment.md

Previous goal turn: no implementation or process evidence remained; current directory contained only specification and .env. This turn starts implementation from that verified state.

Pre-flight interfaces: language/prompt -> runner uses ExpandedCase and exact LlmRequest; JSON/HTTP -> runner uses exact section 8.2 types; score/stats -> runner maps stored terminal records to TrialObservation.

Ruling: Greenfield project is implemented in the explicitly supplied workspace, with a new local Git repository; no separate worktree is needed because there is no existing checkout or branch to isolate.

Ruling: Parallel implementers own separate module directories. The supplied specification already defines the design, so no new design approval is required; the goal explicitly requests execution.

Live spending: the user clarified that auto-charge is disabled and asked to proceed without worrying about cost. Execute with explicit numerical CLI caps from the specification; no additional permission is needed for the authorized experiment.

## Implementation verification (2026-10-06 JST)

Tasks 1-11 are implemented. This is implementation evidence, not a measured syntax effect.

| Requirement | Evidence |
| --- | --- |
| Tasks 1-2: build, JSON, HTTP, Responses | Fresh integrated `./scripts/sbt.ps1 test`: 28/28 core tests passed; durable transcript `verification/integrated-sbt-test.log`. Library dependency inspection lists only scala3-library 3.3.8. |
| Tasks 3-6: language, generation, pairing, prompts | Full bench run passed bootstrap/oracle/syntax/generator/distribution/table/prompt/tasks suites. Generator tests audit 10,000 ASTs with round trips, independent oracles and metamorphisms. Persisted dataset audit describes kind counts and validates all eight nonce-set counts. |
| Tasks 7-8: runner and scoring | Runner/safety/cli/preflight/score/report suites passed. Includes actual concurrent four-worker caps, retries, known vs ambiguous outcomes, durable count starts, interruption reporting, UTC rollover, unknown reservations, byte-checked terminal backing, and refusal to overwrite interrupted preflight. |
| Tasks 9-10: tasks, statistics, freeze | Task/score/stats/freeze suites passed, including family-based bootstrap, degenerate/missing cases and direct Runner freeze guard. |
| Task 11: fresh offline workflow | P0 smoke20 and P1 smoke60 complete synthetic fixtures; plan → audit → run → score → report passed. All ten report artifacts regenerate byte-identically. |
| P1 saved plans | Pilot: 1,800 requests /72 distinct structural families. Main: 5,120 requests /512 families. Both saved and independently audited. |

Full bench verification: **16 suites passed**. Preflight has twelve named offline tests inside its suite. The required integrated full test exited zero at `2026-10-05T17:54:58Z`. Tests use loopback or synthetic clients; no model-accuracy result follows from those tests. The integrated fixes distinguish first close-kind/name errors, protect T3 statement/table order, share exact persisted/transmitted request serialization, and save the separate preflight count body.

Fresh independent code review found significant recovery/accounting/integrity issues. Regression checks and corrections now preserve actual dispatched attempts, known rejection outcomes, saved usage before resume, immutable scoring provenance, compatible preflight settings and matched scheduling boundaries. Reviewer follow-up: no remaining Critical or Important findings; ready for final verification.

## Reproducible saved runs

- `runs/verified-smoke-p0`: complete synthetic 20-request wiring check.
- `runs/verified-smoke-p1`: complete synthetic 60-request wiring check.
- `runs/verified-smoke-v2-p0` and `runs/verified-smoke-v2-p1`: fresh current-source synthetic 20/60 workflows; all ten report artifacts reproduced byte-identically, with zero external HTTP. Witness `verification/offline-v2-reproduction.json`, independently verified against actual files.
- `runs/verified-pilot-p1`: complete exploratory source-v1 live pilot; 1,800 terminal/evaluable, 1,622 strict correct. Full blind assessment and historical provisional counts are recorded in `docs/pilot-assessment.md`.
- `runs/verified-pilot-p1-complete-v1`: preserved complete copy; archived v1 CLI reproduced all five report artifacts byte-identically, with zero HTTP (`verification/pilot-v1-completion.json`).
- `runs/verified-main-p1`: old-source offline initial plan; unfrozen, unexecuted, and ineligible for the integrated source.
- `runs/main-terra`: fresh integrated-source main, independently audited, frozen and executing; 5,120 requests /512 structural families. Protocol `f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a`, source `1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1`. Freeze at `2026-10-05T18:03:32.522947300Z` records commit `74f9240c5d9b27ab7a0c5d5db7c2b9aee79bfca7` and `dirtyTree=false`; frozen request paths passed a dry CLI check with zero HTTP.

The offline CLI main run and preflight commands without `--execute` were verified to send zero HTTP requests.

## Live evidence

`runs/preflight-terra`: succeeded at 2026-10-05T15:41:48Z; requested/returned model `gpt-5.6-terra`, effort low, reading output cap8192. Count and generation: two HTTP requests, 29 known tokens, zero unresolved reservations. `capabilities.json` records verified=true and real response/request provenance.

`runs/smoke-terra`: live P0 smoke20 completed with generation cap20, HTTP cap80 and local token cap250,000. Actual40 HTTP requests,35,472 known tokens, zero unresolved reservations. Strict16/20 correct; four wrong answers, no format/infrastructure/refusal/incomplete outcomes. Generic/named/padded ends4/4 each; typed end3/4; braces1/4. Primary D−B0pp over four families; smoke confidence interval not computed. These tiny operational results do not establish a syntax ranking.

`runs/verified-pilot-p1`: completed all 1,800 generations with 3,600 HTTP attempts and 6,148,886 known tokens; zero unresolved reservations, retries, infrastructure missingness, refusal, incomplete or invalid-format outcomes. Final report completed `2026-10-05T17:51:53Z`. Strict accuracy is 1,622/1,800; T1 after-close pooled accuracy is 306/360. Both prespecified blind difficulty triggers were false, retaining the original main grid. Pilot-v1 preserves normalized logical requests, not captured original wire text; retain that deviation and do not pool pilot with main.

`runs/preflight-terra-v2`: fresh integrated-source probe passed at `2026-10-05T17:55:40.117919Z`, with matching model/low effort/8,192 reading cap, two HTTP attempts, 29 known tokens, zero unresolved reservations, and separate exact generation/count requests saved.

Remaining experiment work: complete all 5,120 frozen main trials and report actual paired results, accounting, provenance, and reproduction. The conditional harder pilot was not triggered; the active experiment goal is not complete.

## Main execution readiness

The main runner selects complete pending five-style blocks before dispatch when its generation limit is a multiple of five. Run/resume resource batches therefore preserve the saved randomized schedule; they do not select cases by observed performance. HTTP limits must include token counts plus permitted generation attempts, and token caps are cumulative within a run directory's UTC ledger window.

Avoid a main invocation crossing 00:00 UTC. Current cancellation handling can finalize a trial counted before generation as `not_dispatched`, and terminal trials are excluded on resume. Check the current UTC margin before each small matched-block batch; leave substantial margin and defer untouched trials to the next window when necessary. Allow the rolling one-minute throttle window to expire between invocations. These operational pauses do not change the frozen dataset or statistical stopping rule.

The selected main uses T1 / after_close only, depths `[2,4,8,16]`, filler `[0,32,128,512]`, 32 families per cell, natural +nonce, five styles, one replicate, eight fixed examples, master/bootstrap seed `20261005`, requested model `gpt-5.6-terra`, low effort and reading cap8,192. These settings were retained from the complete blind assessment before reading directional pilot comparisons.

Live orchestration was fixed before main dispatch: additional generation batches of at most **500** trials, HTTP cap **four times the selected batch size**, and **80,000,000** cumulative tokens per UTC day in the main run directory. The last clean batch is 120 trials/480 HTTP attempts. Keep a minimum 60-second gap between invocations; do not launch substantial work from 22:00 UTC, and resume after 00:05 UTC if needed. The default 120-minute UTC margin is operational caution, not a guaranteed runtime bound. The ignored helper `verification/run-bounded-batches.ps1` checks terminal hashes, unchanged accepted snapshots, usage/reservations, actual frozen hash, and complete five-style blocks; its independent review and four pure decision checks passed. Its first actual 500-trial batch started `2026-10-05T18:06:38.1091523Z`, in active session3244. Main always stops at its fixed count or recorded resource/infrastructure conditions, never an observed effect.

Independent saved-data audit `verification/main-holdout-audit.json` verifies all 16 cells ×32 families, 512 unique families /5,120 unique cases, exact two lexical regimes ×five styles, one replicate, all eight nonce maps assigned four families per cell, and zero pilot-family-ID overlap. A narrow actual first-block probe (`verification/main-request-integrity-probe.json`) verifies all five saved generation/count requests against shared serialization, frozen request hashes, 25 backing artifact hashes, matching requested/returned models, common low/8,192 settings, known usage30,657 settled and zero unresolved reservations for those five IDs. This is artifact/code evidence for one block, not a network capture or whole-run integrity claim.

An independently reviewed candidate harder-reading plan can be prepared offline before pilot completion. It remains conditional and unexecuted until the recorded blind trigger is assessed; it is not a main-grid selection.

Conditional candidate `runs/harder-reading-candidate-p0` is now prepared and audited: 720 T1 requests /36 families, depths `[8,16,32]`, fillers `[128,512,2048]`, fresh seed `2026100601`, protocol `a8e4f3eae24deb60e7e135ce1339efaa9838fb7e409466ced2ff8fb748e4bd22`. Independent offline checks cover all prefixes, both oracles, saved gold, full parsing, and depth-32 AST/JSON round trips. Maximum request text is 36,859 UTF-8 bytes; live token counts remain unmeasured. No HTTP or generation was performed and the candidate has not been selected. Definitions and distributions are saved in its `candidate-audit.md`.

## Implementation rulings

- JSON helpers use `locality.llm.json`; runner scheduling is integrated with Runner rather than a separate Scheduler file. Public required LlmClient types and behaviors are retained.
- Kind imbalance is described and preserved. The specification supplies no kind exclusion threshold, so the audit does not resample or discard cases to force uniformity.
- Token ledgers are scoped to a run directory/UTC date. Preflight and separate directories add spending; explicit caps must be allocated together when bounding total experiment usage.
- API documentation was checked against official sources on2026-10-05; live compatibility is recorded separately. No model fallback, repair, best-of-N or condition-dependent tuning is used.
