# Full-program parsing and generation extension

This document describes the current implementation and verification state. Historical standalone-mock artifacts remain separate, byte-preserved evidence; the mock's zero-live status is not the current P2 execution status. A saved [live execution snapshot](../experiments/data/supporting/p2-live-current-status.json) reports progress only, not graded accuracy or confidence intervals.

The user requested manual parsing problems and clarified the target: given an unfamiliar grammar, which structures can an AI correctly parse and generate? This extends the experiment with full-program structure tasks. The already frozen reading main remains a separate run with its original estimand and source; it is not relabelled as a whole-program parsing experiment.

## Tasks and grading

- `source_to_ast`: give the complete balanced source, grammar description and eight fixed solved examples. Ask for its complete neutral JSON AST. The TEST section contains no target AST, observation, probe, repair instruction or omitted suffix.
- `ast_to_source`: retain the existing shuffled neutral AST table and require complete source in the supplied grammar. Use the same structural families, names, values, ordered statements, example ASTs, lexical mappings and five styles as the parsing direction.
- Both directions have `view=complete` and output cap 16,384. Parse/generate are reported separately; table input and JSON output are different representations, so their absolute accuracies do not isolate direction alone.

Neutral JSON consists of a root `{"body":[...]}`. Statements are `{"type":"scope","kind":"unit|func|area","name":"n_abcdefgh","body":[...]}`, `{"type":"let","variable":"x_a","value":1739}` or `{"type":"nop","payload":"p_abcdefgh"}`. Kind values name semantic categories regardless of the supplied vocabulary. JSON object-key order and whitespace are immaterial; body-array order is significant. Unknown/missing fields, unsupported tags, invalid identifier/value types, duplicate JSON keys and malformed ASTs fail strict grading. Decode into `Program` and compare every node, parent-child relation, kind, name, variable, value, payload and statement order with separately saved gold. A schema-valid wrong tree remains a model failure. Preserve refusal/incomplete, infrastructure missingness and unexecuted denominators.

## Fixed exploratory P2 run

New phase `p2` permits explicitly selected `ast_to_source`/`source_to_ast` tasks in smoke/pilot. Legacy P0/P1 task selection and counts remain unchanged. P2 main/freeze is rejected until a distinct confirmatory plan and matching capabilities are designed.

The fixed pilot is 48 structural families: depths `[2,4,8,16]`, filler statements `[0,8,32]`, four families per cell. Two directions × natural/nonce × five styles × one replicate gives **960 real generations**, or 96 per direction/style. Master and bootstrap seeds are `2026100602`; eight examples; model `gpt-5.6-terra`, effort `low`. Report task/style/depth/filler/lexical rates, exact failure classes, usage, missingness and family-cluster contrasts. Never pool with historical pilot/main, retry wrong answers, remove failures or stop for significance. Mock runs are labelled synthetic.

The grammar is explicitly explained in the prompt. This measures the use of supplied unfamiliar miniature grammars, not inference from examples alone or proven absence from training. Covered structures are generated nested scopes, mixed scope kinds, ordered sibling/leaf statements and closing sequences. The finite grid does not cover arbitrary grammars or all trees.

Depth and deepest-body filler count are controlled factors. Each family currently contains a nested chain and two shallow sibling scopes; branching width, sibling location and homogeneous versus mixed-kind nesting are not independently varied. Depth also changes total node count and output length. Report performance for these sampled structures without treating it as a general taxonomy of parseable grammars. Exact AST correctness includes copying identifiers, values and payloads; the first mismatch diagnostic is not a separate structural-only or partial-node score.

Implementation and tests run in the isolated `feat/full-program-parse-generate` checkout. Create new source-bound artifacts after the full required `./scripts/sbt.ps1 test`, audit and offline reproduction. Live execution needs explicit numerical CLI limits and retains requests, responses, attempts, IDs, reservations and usage.

## Current verification state

As of 2026-10-06, the post-change implementation passed both native `sbt test` and the repository-required `./scripts/sbt.ps1 test` in the local publication checkout on JDK 21 and sbt 1.10.7: 28 core tests, 19 bench suites, 12 named preflight checks and 13 parsing cases. The same production source hash `41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96` also passed saved [CI run 37419923104, job 112126775863](https://github.com/kmizu/syntax-locality-experiments/actions/runs/37419923104/job/112126775863).

The distinct standalone [P2 mock run 37371856651, attempt 3](https://github.com/kmizu/syntax-locality-experiments/actions/runs/37371856651/attempts/3), [job 112109491633](https://github.com/kmizu/syntax-locality-experiments/actions/runs/37371856651/job/112109491633), completed 960 synthetic trials across 48 structural families with `synthetic_mock=true`. All five saved reports were byte-identical on offline reproduction; resume added 0 logical trials and 0 adapter attempts. This verifies the mock workflow, not model performance.

A fresh real capability probe matched the saved P2 source/protocol, model, `low` effort and 16,384 output-token cap. Its count plus generation used 2 HTTP attempts, 29 known tokens and no unresolved reservation. This tiny successful response verifies those request settings and transport support, not full-program model performance. The standard CLI preflight uses 8,192 output tokens; the [public P2 helper and native sbt instructions](../README.md) provide the matching-cap probe. The helper's six synthetic fixture cases and dry-run also pass, with zero live model calls in the publication checkout.

Real P2 execution is underway under its separate saved plan. Its current non-synthetic progress is in [p2-live-current-status.json](../experiments/data/supporting/p2-live-current-status.json). An execution heartbeat is not a graded report: no P2 accuracy, effect or CI is inferred from it. Saved graded P2 reports, when available, are presented separately from historical mock and P1 results. The frozen reading main remains a separate estimand and is never relabelled as generation confirmation.

Historical local limitation: an earlier required-test attempt failed during project loading because the offline cache lacked Scala 2.12.20 metadata and the Scala 3.3.8 compiler cache was inaccessible under the restricted account (`verification/restricted-local-cache-sbt-test.log`). Remote CI success does not establish that the restricted local build passed.
