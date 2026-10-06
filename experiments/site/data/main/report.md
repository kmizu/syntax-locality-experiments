# Syntax locality ICL experiment report

## 1. Run status and summary

Status: **provisional / incomplete run**. synthetic_mock=false.

Strict model-evaluable accuracy: 87.169%; observed primary D−B: NA percentage points.

## 2. Experiment conditions

Preset: main; phase: p1; model: `gpt-5.6-terra`; started: 2026-10-05T17:57:14.347034700Z; protocol version: 1; protocol hash: `f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a`.

Bootstrap seed: 20261005; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: natural, nonce.

```
{"bootstrapSeed":20261005,"concurrency":4,"depths":[2,4,8,16],"fewShotCount":8,"fillersGeneration":[0,8,32],"fillersReading":[0,32,128,512],"generatorVersion":"1","masterSeed":20261005,"maxOutputTokensGeneration":16384,"maxOutputTokensReading":8192,"maxRpm":6E+1,"maxTpm":2E+5,"model":"gpt-5.6-terra","promptVersion":"1","reasoningEffort":"low","replicates":1,"scorerVersion":"1","seedsPerCell":32,"sourceHash":"1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1"}
```

## 3. Planned / executed counts and missingness

| planned | dispatched | terminal | model-evaluable | correct | infrastructure missing | not-dispatched | incomplete | refusal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5120 | 1022 | 1025 | 1021 | 890 | 1 | 4098 | 0 | 0 |

Primary matched families: 19 / 512. Operational success correct/dispatched: 87.084%; upper bound if unresolved dispatched outcomes all succeed: 87.182%.

Missing trials are not model errors. The sensitivity bounds below assign all non-evaluable planned comparison outcomes against / in favor of D.

## 4. Primary paired D−B comparison

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − generic_end | 19/512 | NA | not_computed | 5 | not_computed |

Relative error reduction: NA (NA at zero baseline error). Missingness sensitivity: [-79.297, 80.859] percentage points.

Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.

## 5. Five syntaxes: accuracy, truncation, and usage

| task | view | style | evaluable | accuracy | incomplete | input known | output known | reasoning known | cached known | tokens/correct | retry rate | mean latency ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | braces | 204 | 73.039% | 0 | 615445 | 33388 | 31756 | 0 | 4354.584 | 0.000% | 17981.473 |
| scope_lookup | after_close | generic_end | 204 | 88.725% | 0 | 624070 | 33719 | 32089 | 0 | NA | 0.000% | 9599.405 |
| scope_lookup | after_close | named_end | 204 | 92.647% | 0 | 676430 | 25038 | 23412 | 0 | 3711.471 | 0.000% | 17584.922 |
| scope_lookup | after_close | padded_end | 204 | 92.647% | 0 | 656187 | 34454 | 32822 | 0 | 3654.185 | 0.000% | 16059.683 |
| scope_lookup | after_close | typed_end | 205 | 88.780% | 0 | 642301 | 31071 | 29433 | 0 | 3699.846 | 0.000% | 10307.068 |

Known tokens from unique final trial usage: 3372103; known generation-attempt tokens across retries: 3372103; trials without full input/output usage: 4099; generation attempts without full usage: 1.

HTTP attempts (count, generation, retry): 2047; known estimated cost USD: NA; cost-known trials: 0 / 5120. Estimates are not verified billing or free-quota balances.

Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.

## 6. Lexical regime, depth, and filler breakdown

Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.

| lexical | depth | filler | planned | evaluable | accuracy | infra missing |
| --- | --- | --- | --- | --- | --- | --- |
| natural | 2 | 0 | 160 | 35 | 77.143% | 0 |
| natural | 2 | 32 | 160 | 35 | 94.286% | 0 |
| natural | 2 | 128 | 160 | 15 | 100.000% | 0 |
| natural | 2 | 512 | 160 | 25 | 100.000% | 0 |
| natural | 4 | 0 | 160 | 35 | 57.143% | 0 |
| natural | 4 | 32 | 160 | 35 | 80.000% | 0 |
| natural | 4 | 128 | 160 | 35 | 94.286% | 0 |
| natural | 4 | 512 | 160 | 20 | 95.000% | 0 |
| natural | 8 | 0 | 160 | 30 | 86.667% | 0 |
| natural | 8 | 32 | 160 | 25 | 84.000% | 0 |
| natural | 8 | 128 | 160 | 35 | 91.429% | 0 |
| natural | 8 | 512 | 160 | 37 | 91.892% | 1 |
| natural | 16 | 0 | 160 | 30 | 90.000% | 0 |
| natural | 16 | 32 | 160 | 25 | 96.000% | 0 |
| natural | 16 | 128 | 160 | 30 | 96.667% | 0 |
| natural | 16 | 512 | 160 | 45 | 93.333% | 0 |
| nonce | 2 | 0 | 160 | 25 | 72.000% | 0 |
| nonce | 2 | 32 | 160 | 25 | 76.000% | 0 |
| nonce | 2 | 128 | 160 | 15 | 86.667% | 0 |
| nonce | 2 | 512 | 160 | 25 | 96.000% | 0 |
| nonce | 4 | 0 | 160 | 45 | 68.889% | 0 |
| nonce | 4 | 32 | 160 | 25 | 72.000% | 0 |
| nonce | 4 | 128 | 160 | 40 | 90.000% | 0 |
| nonce | 4 | 512 | 160 | 29 | 100.000% | 0 |
| nonce | 8 | 0 | 160 | 55 | 78.182% | 0 |
| nonce | 8 | 32 | 160 | 35 | 77.143% | 0 |
| nonce | 8 | 128 | 160 | 20 | 90.000% | 0 |
| nonce | 8 | 512 | 160 | 30 | 96.667% | 0 |
| nonce | 16 | 0 | 160 | 35 | 91.429% | 0 |
| nonce | 16 | 32 | 160 | 55 | 96.364% | 0 |
| nonce | 16 | 128 | 160 | 45 | 95.556% | 0 |
| nonce | 16 | 512 | 160 | 25 | 88.000% | 0 |

## 7. Exploratory paired comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − padded_end | 19/512 | NA | not_computed | 3 | not_computed |
| scope_lookup | after_close | named_end − typed_end | 19/512 | NA | not_computed | 3 | not_computed |
| scope_lookup | after_close | typed_end − generic_end | 19/512 | NA | not_computed | 7 | not_computed |
| scope_lookup | after_close | named_end − braces | 19/512 | NA | not_computed | 6 | not_computed |

All D−E, D−C, C−B, A contrasts, other tasks/views, and pilot comparisons are exploratory unless a separate testing plan was frozen. See [paired-comparisons.csv](paired-comparisons.csv).

## 8. Reading and generation

Tasks actually planned in this saved run: scope_lookup.

Compare task-specific effects and output tokens in the tables; a reading improvement does not imply a generation improvement.

## 9. Representative failures

### D correct / B wrong

**named_end**; trial `010a370b4bcf71f5168d61613ec42b3e3c0dd5fbac86bc5fa3768e7a6d9a274a`; outcome `correct`; gold `8409`.

Input:

Full input: [failure-inputs/010a370b4bcf71f5168d61613ec42b3e3c0dd5fbac86bc5fa3768e7a6d9a274a.txt](failure-inputs/010a370b4bcf71f5168d61613ec42b3e3c0dd5fbac86bc5fa3768e7a6d9a274a.txt). Excerpt lines 1–40 of 387:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end func n_xubbtrah
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end area n_vatidpaw
func n_bitcouov
```

Raw answer:

```
8409
```

**generic_end**; trial `f3f4cc96700a80709f8e60aca298d767c82db8bb5b94e263d940c930549370d0`; outcome `wrong_answer`; gold `8409`.

Input:

Full input: [failure-inputs/f3f4cc96700a80709f8e60aca298d767c82db8bb5b94e263d940c930549370d0.txt](failure-inputs/f3f4cc96700a80709f8e60aca298d767c82db8bb5b94e263d940c930549370d0.txt). Excerpt lines 1–40 of 387:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end on its own line.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end
func n_bitcouov
```

Raw answer:

```
7539
```

**named_end**; trial `081fe63a73256f4e78c15f4772e259dee79da0678752855bcb2895d99d0645dd`; outcome `correct`; gold `6019`.

Input:

Full input: [failure-inputs/081fe63a73256f4e78c15f4772e259dee79da0678752855bcb2895d99d0645dd.txt](failure-inputs/081fe63a73256f4e78c15f4772e259dee79da0678752855bcb2895d99d0645dd.txt). Excerpt lines 1–40 of 302:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end func n_xubbtrah
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end area n_vatidpaw
func n_bitcouov
```

Raw answer:

```
6019
```

**generic_end**; trial `7c740bee6d7f2d8d22bcf6d0381d0346b5ccfcdf88e426f0c92325d2e990ee01`; outcome `wrong_answer`; gold `6019`.

Input:

Full input: [failure-inputs/7c740bee6d7f2d8d22bcf6d0381d0346b5ccfcdf88e426f0c92325d2e990ee01.txt](failure-inputs/7c740bee6d7f2d8d22bcf6d0381d0346b5ccfcdf88e426f0c92325d2e990ee01.txt). Excerpt lines 1–40 of 302:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end on its own line.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end
func n_bitcouov
```

Raw answer:

```
2700
```

**named_end**; trial `0c6858f6b88d2f3710de879429f75db375503d11b853c48f0813b785c83f5c89`; outcome `correct`; gold `2922`.

Input:

Full input: [failure-inputs/0c6858f6b88d2f3710de879429f75db375503d11b853c48f0813b785c83f5c89.txt](failure-inputs/0c6858f6b88d2f3710de879429f75db375503d11b853c48f0813b785c83f5c89.txt). Excerpt lines 1–40 of 269:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end func n_xubbtrah
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end area n_vatidpaw
func n_bitcouov
```

Raw answer:

```
2922
```

**generic_end**; trial `1da2a2c7f6e16819321a87c2ad6d008c0b0f53a2088f27cb28018d848cd0f1c1`; outcome `wrong_answer`; gold `2922`.

Input:

Full input: [failure-inputs/1da2a2c7f6e16819321a87c2ad6d008c0b0f53a2088f27cb28018d848cd0f1c1.txt](failure-inputs/1da2a2c7f6e16819321a87c2ad6d008c0b0f53a2088f27cb28018d848cd0f1c1.txt). Excerpt lines 1–40 of 269:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end on its own line.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end
func n_bitcouov
```

Raw answer:

```
5557
```

### D wrong / B correct

**named_end**; trial `08f28ea6d6b87b1eaddefb8eb637a19d4bd96028270f2fe15566d8705abdcd8f`; outcome `wrong_answer`; gold `8351`.

Input:

Full input: [failure-inputs/08f28ea6d6b87b1eaddefb8eb637a19d4bd96028270f2fe15566d8705abdcd8f.txt](failure-inputs/08f28ea6d6b87b1eaddefb8eb637a19d4bd96028270f2fe15566d8705abdcd8f.txt). Excerpt lines 1–40 of 418:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end func n_xubbtrah
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end area n_vatidpaw
func n_bitcouov
```

Raw answer:

```
8241
```

**generic_end**; trial `358d0fefaf9ae2f5215f97d38fb70d5349a5a085b64cc29407636e0015df9d0e`; outcome `correct`; gold `8351`.

Input:

Full input: [failure-inputs/358d0fefaf9ae2f5215f97d38fb70d5349a5a085b64cc29407636e0015df9d0e.txt](failure-inputs/358d0fefaf9ae2f5215f97d38fb70d5349a5a085b64cc29407636e0015df9d0e.txt). Excerpt lines 1–40 of 418:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are unit for unit, func for func, and area for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with end on its own line.
KIND is one of unit, func, area; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is let VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
let x_b 4122
let x_a 2890
func n_xubbtrah
let x_a 3834
nop p_gwsvasbv
end
unit n_svuhbhuc
let x_a 9147
area n_vatidpaw
let x_a 2381
nop p_bmncluwq
end
func n_bitcouov
```

Raw answer:

```
8351
```

**named_end**; trial `15b3107d3086cac432e448fa6e4b0bf326346ffd14d6ebc378d2b9fa88f9c1c6`; outcome `wrong_answer`; gold `5392`.

Input:

Full input: [failure-inputs/15b3107d3086cac432e448fa6e4b0bf326346ffd14d6ebc378d2b9fa88f9c1c6.txt](failure-inputs/15b3107d3086cac432e448fa6e4b0bf326346ffd14d6ebc378d2b9fa88f9c1c6.txt). Excerpt lines 1–40 of 286:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are raku for unit, weso for func, and bifo for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with hud KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of raku, weso, bifo; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is zim VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is vop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
zim x_b 4122
zim x_a 2890
weso n_xubbtrah
zim x_a 3834
vop p_gwsvasbv
hud weso n_xubbtrah
raku n_svuhbhuc
zim x_a 9147
bifo n_vatidpaw
zim x_a 2381
vop p_bmncluwq
hud bifo n_vatidpaw
weso n_bitcouov
```

Raw answer:

```
9299
```

**generic_end**; trial `3919b9eaa5868d4535c77783a3bb14b0e6f50f8230cb412ceae25076d1d5fc55`; outcome `correct`; gold `5392`.

Input:

Full input: [failure-inputs/3919b9eaa5868d4535c77783a3bb14b0e6f50f8230cb412ceae25076d1d5fc55.txt](failure-inputs/3919b9eaa5868d4535c77783a3bb14b0e6f50f8230cb412ceae25076d1d5fc55.txt). Excerpt lines 1–40 of 286:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are raku for unit, weso for func, and bifo for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with hud on its own line.
KIND is one of raku, weso, bifo; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is zim VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is vop PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
zim x_b 4122
zim x_a 2890
weso n_xubbtrah
zim x_a 3834
vop p_gwsvasbv
hud
raku n_svuhbhuc
zim x_a 9147
bifo n_vatidpaw
zim x_a 2381
vop p_bmncluwq
hud
weso n_bitcouov
```

Raw answer:

```
5392
```

**named_end**; trial `28fe6eb35d1da239cc5ddc097dd2e76c1e6066ef0e850740d6440ba2efb04bb3`; outcome `wrong_answer`; gold `4949`.

Input:

Full input: [failure-inputs/28fe6eb35d1da239cc5ddc097dd2e76c1e6066ef0e850740d6440ba2efb04bb3.txt](failure-inputs/28fe6eb35d1da239cc5ddc097dd2e76c1e6066ef0e850740d6440ba2efb04bb3.txt). Excerpt lines 1–40 of 259:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are feni for unit, havu for func, and zomu for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with tek KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of feni, havu, zomu; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is dal VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nup PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
dal x_b 4122
dal x_a 2890
havu n_xubbtrah
dal x_a 3834
nup p_gwsvasbv
tek havu n_xubbtrah
feni n_svuhbhuc
dal x_a 9147
zomu n_vatidpaw
dal x_a 2381
nup p_bmncluwq
tek zomu n_vatidpaw
havu n_bitcouov
```

Raw answer:

```
8568
```

**generic_end**; trial `9f2d6583d99e82290e9a23d3f5f1edb8642e77299dd1e8a5b79a1c16c213bbc1`; outcome `correct`; gold `4949`.

Input:

Full input: [failure-inputs/9f2d6583d99e82290e9a23d3f5f1edb8642e77299dd1e8a5b79a1c16c213bbc1.txt](failure-inputs/9f2d6583d99e82290e9a23d3f5f1edb8642e77299dd1e8a5b79a1c16c213bbc1.txt). Excerpt lines 1–40 of 259:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are feni for unit, havu for func, and zomu for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with tek on its own line.
KIND is one of feni, havu, zomu; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is dal VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is nup PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
dal x_b 4122
dal x_a 2890
havu n_xubbtrah
dal x_a 3834
nup p_gwsvasbv
tek
feni n_svuhbhuc
dal x_a 9147
zomu n_vatidpaw
dal x_a 2381
nup p_bmncluwq
tek
havu n_bitcouov
```

Raw answer:

```
4949
```

### both wrong

**named_end**; trial `e267a9b4f912e5c63fb2de78ceb453fda03970d6bf9698eccf3ca1a7cb1691fa`; outcome `wrong_answer`; gold `6191`.

Input:

Full input: [failure-inputs/e267a9b4f912e5c63fb2de78ceb453fda03970d6bf9698eccf3ca1a7cb1691fa.txt](failure-inputs/e267a9b4f912e5c63fb2de78ceb453fda03970d6bf9698eccf3ca1a7cb1691fa.txt). Excerpt lines 1–40 of 286:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are kori for unit, fazu for func, and benu for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with gol KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of kori, fazu, benu; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is miv VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is zup PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
miv x_b 4122
miv x_a 2890
fazu n_xubbtrah
miv x_a 3834
zup p_gwsvasbv
gol fazu n_xubbtrah
kori n_svuhbhuc
miv x_a 9147
benu n_vatidpaw
miv x_a 2381
zup p_bmncluwq
gol benu n_vatidpaw
fazu n_bitcouov
```

Raw answer:

```
8357
```

**generic_end**; trial `dd53c66605c0a2aa319e56ec156e17137899e27e3fe06c569be945c275aa4480`; outcome `wrong_answer`; gold `6191`.

Input:

Full input: [failure-inputs/dd53c66605c0a2aa319e56ec156e17137899e27e3fe06c569be945c275aa4480.txt](failure-inputs/dd53c66605c0a2aa319e56ec156e17137899e27e3fe06c569be945c275aa4480.txt). Excerpt lines 1–40 of 286:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are kori for unit, fazu for func, and benu for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with gol on its own line.
KIND is one of kori, fazu, benu; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is miv VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is zup PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
miv x_b 4122
miv x_a 2890
fazu n_xubbtrah
miv x_a 3834
zup p_gwsvasbv
gol
kori n_svuhbhuc
miv x_a 9147
benu n_vatidpaw
miv x_a 2381
zup p_bmncluwq
gol
fazu n_bitcouov
```

Raw answer:

```
8357
```

**named_end**; trial `f8033fe62abc210f1f1147d9ce71b586557f77e2e1fb4001810875c2978bec10`; outcome `wrong_answer`; gold `8192`.

Input:

Full input: [failure-inputs/f8033fe62abc210f1f1147d9ce71b586557f77e2e1fb4001810875c2978bec10.txt](failure-inputs/f8033fe62abc210f1f1147d9ce71b586557f77e2e1fb4001810875c2978bec10.txt). Excerpt lines 1–40 of 292:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are vomu for unit, zafi for func, and nelo for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with pab KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of vomu, zafi, nelo; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is dut VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is hev PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
dut x_b 4122
dut x_a 2890
zafi n_xubbtrah
dut x_a 3834
hev p_gwsvasbv
pab zafi n_xubbtrah
vomu n_svuhbhuc
dut x_a 9147
nelo n_vatidpaw
dut x_a 2381
hev p_bmncluwq
pab nelo n_vatidpaw
zafi n_bitcouov
```

Raw answer:

```
1972
```

**generic_end**; trial `d78f8e173f032cbb7b999f79277aa27dc9ec8ae488f8c5ba679773c44914851b`; outcome `wrong_answer`; gold `8192`.

Input:

Full input: [failure-inputs/d78f8e173f032cbb7b999f79277aa27dc9ec8ae488f8c5ba679773c44914851b.txt](failure-inputs/d78f8e173f032cbb7b999f79277aa27dc9ec8ae488f8c5ba679773c44914851b.txt). Excerpt lines 1–40 of 292:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
The kind keywords are vomu for unit, zafi for func, and nelo for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with pab on its own line.
KIND is one of vomu, zafi, nelo; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is dut VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is hev PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
dut x_b 4122
dut x_a 2890
zafi n_xubbtrah
dut x_a 3834
hev p_gwsvasbv
pab
vomu n_svuhbhuc
dut x_a 9147
nelo n_vatidpaw
dut x_a 2381
hev p_bmncluwq
pab
zafi n_bitcouov
```

Raw answer:

```
5145
```


## 10. Limitations and next experiments

The benchmark tests explained miniature grammars plus fixed few-shot examples, not grammar learning from examples alone. The eight fixed nonce vocabularies do not represent every artificial vocabulary. Indentation is non-semantic; D/E character-length matching is not token matching.

Accuracy, token usage, reasoning usage, caching, and latency are observable task and service outcomes; they do not directly measure cognitive load, dependency distance inside a model, or FLOPs. First parse errors are not counts of every syntax error. Self-contained fences are auxiliary normalization only.

An observed zero or undetected difference does not prove general equivalence. Differences can reflect retrieval cues, delimiter identification, tokenization, or prompt length. Missingness, ceilings/floors, generation output burden, and method-specific measurement limits constrain causal explanations.

Follow-up candidates after a new preregistered holdout: indentation, same-kind nesting, number of examples, reasoning effort, repair tasks, local-window cuts, and input-token matching. Do not remove failures or stop when an interval first becomes favorable.
