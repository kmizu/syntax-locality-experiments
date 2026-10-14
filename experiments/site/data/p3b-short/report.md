# Syntax locality ICL experiment report

## 1. Run status and summary

Status: **all planned trials terminal**. synthetic_mock=false.

Strict model-evaluable accuracy: 81.944%; observed primary D−B: NA percentage points.

## 2. Experiment conditions

Preset: p3; phase: p1; model: `gpt-5.6-terra`; started: 2026-10-14T09:22:32.329680600Z; protocol version: 1; protocol hash: `256e503ae744e165c61804b9f6cf92e13073bd66e8f66069f4799f0cd3d4c9d0`.

Bootstrap seed: 20261005; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: natural, nonce.

```
{"bootstrapSeed":20261005,"concurrency":4,"depths":[4,8,16],"fewShotCount":8,"fillersGeneration":[0,32,128],"fillersReading":[0,32,128],"generatorVersion":"1","importQualification":"short","imports":true,"masterSeed":2026101402,"maxOutputTokensGeneration":16384,"maxOutputTokensReading":8192,"maxRpm":6E+1,"maxTpm":2E+5,"model":"gpt-5.6-terra","promptVersion":"1","reasoningEffort":"low","replicates":1,"scorerVersion":"1","seedsPerCell":4,"sourceHash":"f57b680ff77f4a0b4190ede060a71f008bb1671a65d82f100fc9e6a5ffa4aff0","styles":["named_end"],"tasks":["scope_lookup","ast_to_source"]}
```

## 3. Planned / executed counts and missingness

| planned | dispatched | terminal | model-evaluable | correct | infrastructure missing | not-dispatched | incomplete | refusal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 216 | 216 | 216 | 216 | 177 | 0 | 0 | 0 | 0 |

Primary matched families: 0 / 36. Operational success correct/dispatched: 81.944%; upper bound if unresolved dispatched outcomes all succeed: 81.944%.

Missing trials are not model errors. The sensitivity bounds below assign all non-evaluable planned comparison outcomes against / in favor of D.

## 4. Primary paired D−B comparison

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − generic_end | 0/36 | NA | not_computed | 0 | not_computed |

Relative error reduction: NA (NA at zero baseline error). Missingness sensitivity: [-23.611, 76.389] percentage points.

Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.

## 5. Five syntaxes: accuracy, truncation, and usage

| task | view | style | evaluable | accuracy | incomplete | input known | output known | reasoning known | cached known | tokens/correct | retry rate | mean latency ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | named_end | 72 | 69.444% | 0 | 878309 | 79835 | 16330 | 0 | 19162.880 | 0.000% | 24872.250 |
| scope_lookup | after_close | named_end | 72 | 76.389% | 0 | 253294 | 11499 | 10923 | 0 | 4814.418 | 0.000% | 11476.583 |
| scope_lookup | before_close | named_end | 72 | 100.000% | 0 | 240964 | 7110 | 6534 | 0 | 3445.472 | 0.000% | 12616.903 |

Known tokens from unique final trial usage: 1471011; known generation-attempt tokens across retries: 1471011; trials without full input/output usage: 0; generation attempts without full usage: 0.

HTTP attempts (count, generation, retry): 432; known estimated cost USD: NA; cost-known trials: 0 / 216. Estimates are not verified billing or free-quota balances.

Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.

## 6. Lexical regime, depth, and filler breakdown

Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.

| lexical | depth | filler | planned | evaluable | accuracy | infra missing |
| --- | --- | --- | --- | --- | --- | --- |
| natural | 4 | 0 | 12 | 12 | 83.333% | 0 |
| natural | 4 | 32 | 12 | 12 | 66.667% | 0 |
| natural | 4 | 128 | 12 | 12 | 75.000% | 0 |
| natural | 8 | 0 | 12 | 12 | 83.333% | 0 |
| natural | 8 | 32 | 12 | 12 | 91.667% | 0 |
| natural | 8 | 128 | 12 | 12 | 75.000% | 0 |
| natural | 16 | 0 | 12 | 12 | 66.667% | 0 |
| natural | 16 | 32 | 12 | 12 | 83.333% | 0 |
| natural | 16 | 128 | 12 | 12 | 58.333% | 0 |
| nonce | 4 | 0 | 12 | 12 | 100.000% | 0 |
| nonce | 4 | 32 | 12 | 12 | 100.000% | 0 |
| nonce | 4 | 128 | 12 | 12 | 83.333% | 0 |
| nonce | 8 | 0 | 12 | 12 | 83.333% | 0 |
| nonce | 8 | 32 | 12 | 12 | 91.667% | 0 |
| nonce | 8 | 128 | 12 | 12 | 75.000% | 0 |
| nonce | 16 | 0 | 12 | 12 | 66.667% | 0 |
| nonce | 16 | 32 | 12 | 12 | 100.000% | 0 |
| nonce | 16 | 128 | 12 | 12 | 91.667% | 0 |

## 7. Exploratory paired comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ast_to_source | complete | named_end − generic_end | 0/36 | NA | not_computed | 0 | not_computed |
| ast_to_source | complete | named_end − padded_end | 0/36 | NA | not_computed | 0 | not_computed |
| ast_to_source | complete | named_end − typed_end | 0/36 | NA | not_computed | 0 | not_computed |
| ast_to_source | complete | typed_end − generic_end | 0/0 | NA | not_computed | 0 | not_computed |
| ast_to_source | complete | named_end − braces | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | after_close | named_end − padded_end | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | after_close | named_end − typed_end | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | after_close | typed_end − generic_end | 0/0 | NA | not_computed | 0 | not_computed |
| scope_lookup | after_close | named_end − braces | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | before_close | named_end − generic_end | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | before_close | named_end − padded_end | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | before_close | named_end − typed_end | 0/36 | NA | not_computed | 0 | not_computed |
| scope_lookup | before_close | typed_end − generic_end | 0/0 | NA | not_computed | 0 | not_computed |
| scope_lookup | before_close | named_end − braces | 0/36 | NA | not_computed | 0 | not_computed |

All D−E, D−C, C−B, A contrasts, other tasks/views, and pilot comparisons are exploratory unless a separate testing plan was frozen. See [paired-comparisons.csv](paired-comparisons.csv).

## 8. Reading and generation

Tasks actually planned in this saved run: ast_to_source, scope_lookup.

Generation is assessed independently by strict syntax and exact AST equality. Its shorter capped AST bodies and longer correct D/E outputs differ from long-prefix reading tasks.

Compare task-specific effects and output tokens in the tables; a reading improvement does not imply a generation improvement.

## 9. Representative failures

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
