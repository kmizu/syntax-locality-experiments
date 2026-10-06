# Exploratory pilot interpretation after main freeze

The largest observed separation is T3 generation at depth 8. This exploratory analysis was generated `2026-10-05T18:14:58.9930890Z`, **after main freeze** was confirmed under protocol `f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a`. The [blind assessment](pilot-assessment.md) had already retained the original grid at `17:53:33Z`; that decision is unchanged.

Sources: original [report](../runs/verified-pilot-p1/report.md), [cases](../runs/verified-pilot-p1/cases.jsonl), [scores](../runs/verified-pilot-p1/scores.jsonl), [summary](../runs/verified-pilot-p1/summary.csv), and [contrasts](../runs/verified-pilot-p1/paired-comparisons.csv). One-to-one case/score joins reproduce the T3 Scala summary. Protocol `f7dd15dd3c44b507506e123517157926a35a53b52c1d4aab278664f512a1e182`, source `f3e5a0a43c3ca7332be53f098c2b8d9064360312d6e7d5efc16cc4a74fd646ab`, model `gpt-5.6-terra`, effort `low`, `synthetic_mock=false`. All 1,800 observations, including every failure, remain separate from main.

## T3 generation: the largest observed separation is at depth 8

Styles: A braces, B generic end, C typed end, D named end, E padded generic end. Each style has 72 generations over 36 families × two lexical regimes × one replicate. Each style/depth row contains **24 generations from 12 families**, pooling fillers `[0,8,32]` and both regimes.

| Style | Depth | Strict correct / 24 | `unclosed_scope` | `close_mismatch` | `invalid_nop` | Valid syntax, wrong AST |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 2 | 24 | 0 | 0 | 0 | 0 |
| A | 4 | 24 | 0 | 0 | 0 | 0 |
| A | 8 | 10 | 13 | 0 | 0 | 1 |
| B | 2 | 24 | 0 | 0 | 0 | 0 |
| B | 4 | 22 | 2 | 0 | 0 | 0 |
| B | 8 | 0 | 23 | 0 | 1 | 0 |
| C | 2 | 24 | 0 | 0 | 0 | 0 |
| C | 4 | 24 | 0 | 0 | 0 | 0 |
| C | 8 | 12 | 8 | 3 | 0 | 1 |
| D | 2 | 23 | 0 | 0 | 0 | 1 |
| D | 4 | 24 | 0 | 0 | 0 | 0 |
| D | 8 | 24 | 0 | 0 | 0 | 0 |
| E | 2 | 24 | 0 | 0 | 0 | 0 |
| E | 4 | 24 | 0 | 0 | 0 | 0 |
| E | 8 | 1 | 23 | 0 | 0 | 0 |

Parser columns count **first errors per response**. All other T3 failure classes are zero. Overall strict results: A **58/72**, B **46/72**, C **60/72**, D **71/72**, E **49/72**. The 73 invalid syntaxes comprise 69 `unclosed_scope`, three `close_mismatch`, one `invalid_nop`; three other responses have valid syntax but wrong ASTs. D's 72 responses all parse; 71 match the AST.

At depth 8, B scores 0/24, E 1/24, and D 24/24. Most failures first report scopes still open at program end. All 73 invalid responses were provider-status `completed`, with one generation attempt and 151–764 output tokens against a common 16,384 cap. This does not diagnose provider truncation or an internal reasoning mechanism. Original v1 first-error categories remain unchanged.

## T1 after-close: identical marginal rates, different lexical error pairs

Each lexical row compares B/D over **36 matched structural families**, one generation per style. Both regimes reuse those same families.

| Lexical regime | B correct / 36 | D correct / 36 | Both correct | Both wrong | D only correct | B only correct | Discordant families | D−B (pp) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Natural | 30 | 33 | 27 | 0 | 6 | 3 | 9 | +8.333 |
| Nonce | 30 | 33 | 28 | 1 | 5 | 2 | 7 | +8.333 |

Across regimes B scores **60/72 (83.333%)**, D **66/72 (91.667%)**. Discordance covers **13 distinct families** in either regime; 9+7 would double-count shared families. Two opposing lexical disagreements cancel the averaged difference but remain discordant. Equal marginal rates coexist with different errors and do not establish lexical equivalence beyond the eight fixed nonce mappings.

E ties D at **66/72** on T1 after-close, while T3 separates E's 49/72 from D's 71/72. Named closing labels show a large generation advantage in this pilot, especially at depth 8. Reading and generation require separate reporting; these outcomes do not identify the model's internal mechanism or isolate an effect independent of the complete prompt/tokenization conditions.

## Saved primary contrasts and interpretation

Saved intervals use **10,000 depth × filler stratified family-bootstrap draws**, seed `20261005`, averaging natural/nonce within 36 matched families. T1/T2 share reading families; T3 uses separate families. Renderings are not independent clusters. These exploratory intervals do not adjust for inspecting multiple outcomes.

| Task / view | D−B (percentage points) | Saved 95% CI |
| --- | ---: | --- |
| T1 / after_close | +8.333 | [0.000, 16.667] |
| T2 / after_close | +9.722 | [1.389, 19.444] |
| T3 / complete | +34.722 | [30.556, 37.500] |

T1/T2 before-close have perfect B/D scores and degenerate difference/interval 0/[0,0], not general equivalence. T1 after-close's interval includes zero; the pilot does not establish the confirmatory reading hypothesis. Report T3's depth-8 pattern separately while frozen reading main proceeds. No cap change or failure removal is indicated.

## Pilot-v1 provenance limit

Original transport saved complete canonical logical requests but submitted insertion-order object keys; original wire text was not captured byte-for-byte. Content/order, model, effort, caps, and outcomes remain preserved. Reconstructed bodies are not captured raw wire requests. Original source/grades remain unchanged and are not pooled with changed-source main. These explained miniature grammars with eight fixed examples measure task outcomes, not model cognition or general programming competence.
