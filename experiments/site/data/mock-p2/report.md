# Syntax locality ICL experiment report

## 1. Run status and summary

Status: **all planned trials terminal**. synthetic_mock=true.

These synthetic fixtures check experiment wiring. They are not model performance measurements or evidence for a syntax advantage.

Strict model-evaluable accuracy: 100.000%. Both full-program directions are exploratory.

## 2. Experiment conditions

Preset: pilot; phase: p2; model: `gpt-5.6-terra`; started: 2026-10-06T04:35:53.780147264Z; protocol version: 2; protocol hash: `2ac04cc60928800d0c1fdd37986546a6fee23bd1955ccc011c76099cbde60471`.

Bootstrap seed: 2026100602; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: natural, nonce.

```
{"bootstrapSeed":2026100602,"concurrency":2,"depths":[2,4,8,16],"fewShotCount":8,"fillersGeneration":[0,8,32],"fillersReading":[0,8,32],"generatorVersion":"1","masterSeed":2026100602,"maxOutputTokensGeneration":16384,"maxOutputTokensReading":8192,"maxRpm":2E+1,"maxTpm":6E+4,"model":"gpt-5.6-terra","promptVersion":"2","reasoningEffort":"low","replicates":1,"scorerVersion":"2","seedsPerCell":4,"sourceHash":"41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96","tasks":["ast_to_source","source_to_ast"]}
```

## 3. Planned / executed counts and missingness

| planned | dispatched | terminal | model-evaluable | correct | infrastructure missing | not-dispatched | incomplete | refusal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 960 | 960 | 960 | 960 | 960 | 0 | 0 | 0 | 0 |

Operational success correct/dispatched: 100.000%; upper bound if unresolved dispatched outcomes all succeed: 100.000%.

Missing trials are not model errors. Comparison sensitivity bounds in paired-comparisons.csv assign all non-evaluable planned outcomes against / in favor of D.

## 4. Exploratory parsing and generation D−B comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | named_end − generic_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| source_to_ast | complete | named_end − generic_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |

No confirmatory reading-primary comparison is planned in P2. Parsing and generation remain separate outcomes.

Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.

## 5. Five syntaxes: accuracy, truncation, and usage

| task | view | style | evaluable | accuracy | incomplete | input known | output known | reasoning known | cached known | tokens/correct | retry rate | mean latency ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | braces | 96 | 100.000% | 0 | 393008 | 13176 | 0 | 0 | 4231.083 | 0.000% | 8.708 |
| ast_to_source | complete | generic_end | 96 | 100.000% | 0 | 393008 | 13176 | 0 | 0 | 4231.083 | 0.000% | 7.844 |
| ast_to_source | complete | named_end | 96 | 100.000% | 0 | 409704 | 16824 | 0 | 0 | 4443.000 | 0.000% | 8.802 |
| ast_to_source | complete | padded_end | 96 | 100.000% | 0 | 410456 | 16824 | 0 | 0 | 4450.833 | 0.000% | 8.573 |
| ast_to_source | complete | typed_end | 96 | 100.000% | 0 | 398960 | 14280 | 0 | 0 | 4304.583 | 0.000% | 8.354 |
| source_to_ast | complete | braces | 96 | 100.000% | 0 | 320048 | 38732 | 0 | 0 | 3737.292 | 0.000% | 8.573 |
| source_to_ast | complete | generic_end | 96 | 100.000% | 0 | 320048 | 38732 | 0 | 0 | 3737.292 | 0.000% | 8.260 |
| source_to_ast | complete | named_end | 96 | 100.000% | 0 | 340352 | 38732 | 0 | 0 | 3948.792 | 0.000% | 7.969 |
| source_to_ast | complete | padded_end | 96 | 100.000% | 0 | 341120 | 38732 | 0 | 0 | 3956.792 | 0.000% | 8.219 |
| source_to_ast | complete | typed_end | 96 | 100.000% | 0 | 327140 | 38732 | 0 | 0 | 3811.167 | 0.000% | 7.865 |

Known tokens from unique final trial usage: 3921784; known generation-attempt tokens across retries: 3921784; trials without full input/output usage: 0; generation attempts without full usage: 0.

HTTP attempts (count, generation, retry): 1920; known estimated cost USD: NA; cost-known trials: 0 / 960. Estimates are not verified billing or free-quota balances.

Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.

## 6. Lexical regime, depth, and filler breakdown

Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.

| task | view | lexical | depth | filler | planned | evaluable | accuracy | infra missing |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | natural | 2 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 2 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 2 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 4 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 4 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 4 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 8 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 8 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 8 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 16 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 16 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | natural | 16 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 2 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 2 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 2 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 4 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 4 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 4 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 8 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 8 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 8 | 32 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 16 | 0 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 16 | 8 | 20 | 20 | 100.000% | 0 |
| ast_to_source | complete | nonce | 16 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 2 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 2 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 2 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 4 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 4 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 4 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 8 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 8 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 8 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 16 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 16 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | natural | 16 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 2 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 2 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 2 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 4 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 4 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 4 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 8 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 8 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 8 | 32 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 16 | 0 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 16 | 8 | 20 | 20 | 100.000% | 0 |
| source_to_ast | complete | nonce | 16 | 32 | 20 | 20 | 100.000% | 0 |

## 7. Exploratory paired comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | named_end − padded_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| ast_to_source | complete | named_end − typed_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| ast_to_source | complete | typed_end − generic_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| ast_to_source | complete | named_end − braces | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| source_to_ast | complete | named_end − padded_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| source_to_ast | complete | named_end − typed_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| source_to_ast | complete | typed_end − generic_end | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| source_to_ast | complete | named_end − braces | 48/48 | 0.000 | [0.000, 0.000] | 0 | degenerate |

All D−E, D−C, C−B, A contrasts, other tasks/views, and pilot comparisons are exploratory unless a separate testing plan was frozen. See [paired-comparisons.csv](paired-comparisons.csv).

## 8. Full-program parsing and generation

Tasks actually planned in this saved run: ast_to_source, source_to_ast.

Source-to-AST is manual parsing: the model receives complete rendered source and returns nested AST JSON without using a parser tool. AST-to-source receives a shuffled AST table and returns complete program source. The directions share structural families and the example bank, but their input/output representations are not symmetric. Exact AST equality is scored separately for each direction.

For source_to_ast, syntax_valid and strict_syntax_validity describe the returned JSON AST schema, not the source language's syntax. For ast_to_source they describe the generated source grammar. Schema-valid but incorrect ASTs fail strict correctness. AST codec and mismatch diagnostics are retained in scores.jsonl, errors.csv, and the failure examples.

Compare task-specific effects and output tokens in the tables; parsing and generation improvements are separate observations.

## 9. Representative failures

### ast_to_source

### D correct / B wrong

No eligible paired failures.

### D wrong / B correct

No eligible paired failures.

### both wrong

No eligible paired failures.

### source_to_ast

### D correct / B wrong

No eligible paired failures.

### D wrong / B correct

No eligible paired failures.

### both wrong

No eligible paired failures.


## 10. Limitations and next experiments

The benchmark tests explained miniature grammars plus fixed few-shot examples, not grammar learning from examples alone. The eight fixed nonce vocabularies do not represent every artificial vocabulary. Indentation is non-semantic; D/E character-length matching is not token matching.

Accuracy, token usage, reasoning usage, caching, and latency are observable task and service outcomes; they do not directly measure cognitive load, dependency distance inside a model, or FLOPs. First parse errors are not counts of every syntax error. Self-contained fences are auxiliary normalization only.

An observed zero or undetected difference does not prove general equivalence. Differences can reflect retrieval cues, delimiter identification, tokenization, or prompt length. Missingness, ceilings/floors, generation output burden, and method-specific measurement limits constrain causal explanations.

Follow-up candidates after a new preregistered holdout: indentation, same-kind nesting, number of examples, reasoning effort, repair tasks, local-window cuts, and input-token matching. Do not remove failures or stop when an interval first becomes favorable.
