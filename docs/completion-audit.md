# Experiment completion audit

Status: **not complete**. The full P1 pilot and blind assessment are complete; the fresh main is audited, frozen and executing. Final main measurements remain pending.

| Requirement | Authoritative evidence | Remaining work |
| --- | --- | --- |
| Scala 3.3.8, JDK 21, sbt 1.10.7; standard libraries only | Required ./scripts/sbt.ps1 test exited zero at 2026-10-05T17:54:58Z; verification/integrated-sbt-test.log; inspected dependency classpaths | Preserve verified production source |
| Independent AST/event oracles, five syntaxes, paired gold, 10,000 AST audit | Full oracle/syntax/generator/distribution suites and new main CLI audit passed | Preserve frozen source/data |
| Offline plan/audit/mock/score/report | Fresh current-source synthetic runs/verified-smoke-v2-p0 and -p1:20/60 planned/terminal/scored;all ten artifact hashes reproduced byte-identically (verification/offline-v2-reproduction.json), independently checked | Keep synthetic data separate |
| HTTP, retries, reservations, crash/resume, UTC ledgers | 28 core tests and runner/safety/CLI/preflight suites; exact shared request serialization integrated | Check actual main accounting after each batch |
| T2/T3 strict grading and diagnostics | Integrated kind/name-first diagnostics and statement/table-order regressions pass full suites | Retain original v1 parser for its reports |
| Cluster statistics, missingness, degeneracy, freeze/hash checks | Stats/bootstrap/freeze suites pass; fresh 5,120-request /512-family plan audited | Actual main cluster analysis |
| Real model compatibility and exact new request capture | Matching real v2 capabilities used at freeze; first five-style actual block serializer/backing/usage probe passed (verification/main-request-integrity-probe.json) | Full-run backing/accounting verification |
| Actual smoke | v1 20/20 terminal,strict16/20;zero infrastructure missingness | Preserve operational scope/capture limitation |
| Full P1 pilot | Original job32437 run/score/report exited zero;1,800 terminal/evaluable,strict1,622;6,148,886 known tokens,3,600HTTP,zero unresolved/retries | Do not pool with main |
| Blind pilot-to-main decision | verification/blind-pilot-v1-assessment.json; unique joins/hashes/CSV cross-checks;both triggers false; original grid committed74f9240 then frozen before directional inspection | Preserve fixed settings |
| Conditional larger reading grid | Old offline candidate audited;blind trigger false;no candidate HTTP | No harder pilot required |
| Reproducible v1 reports | Complete copy/archived source/runtime;all five final report hashes reproduced byte-identically without HTTP (verification/pilot-v1-completion.json) | Retain originals and witness |
| Confirmatory main/final reporting | runs/main-terra:5,120 trials/512 families;protocol f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a frozen18:03:32UTC;actual execution started18:06:38UTC | Complete fixed count,saved-artifact reports/CI,final reproduction |
| Repository provenance | AGENTS/CLAUDE byte-identical;new source1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1;freeze records committed assessment74f9240 with dirtyTree=false | Final artifact ledger |

The three identified implementation gaps are fixed and verified. Historical v1 runs saved complete canonical logical requests rather than original insertion-order wire text; that real capture deviation cannot be restored retrospectively. Unchanged prompts/settings and archived strict scores support the recorded exploratory blind difficulty assessment. Fresh main uses exact shared persistence/transport and remains separate from v1 observations. No directional hypothesis conclusion is supported yet; the requested experiment requires actual main measurements.
