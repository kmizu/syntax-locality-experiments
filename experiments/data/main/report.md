# Syntax locality ICL experiment report

## 1. Run status and summary

Status: **provisional / incomplete run**. synthetic_mock=false.

Strict model-evaluable accuracy: 86.719%; observed primary D−B: 5.742 percentage points.

## 2. Experiment conditions

Preset: main; phase: p1; model: `gpt-5.6-terra`; started: 2026-10-05T17:57:14.347034700Z; protocol version: 1; protocol hash: `f57aadd6b497ff6d05715753dd59802be050dce8ec5706ff41216721515e6f4a`.

Bootstrap seed: 20261005; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: natural, nonce.

```
{"bootstrapSeed":20261005,"concurrency":4,"depths":[2,4,8,16],"fewShotCount":8,"fillersGeneration":[0,8,32],"fillersReading":[0,32,128,512],"generatorVersion":"1","masterSeed":20261005,"maxOutputTokensGeneration":16384,"maxOutputTokensReading":8192,"maxRpm":6E+1,"maxTpm":2E+5,"model":"gpt-5.6-terra","promptVersion":"1","reasoningEffort":"low","replicates":1,"scorerVersion":"1","seedsPerCell":32,"sourceHash":"1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1"}
```

## 3. Planned / executed counts and missingness

| planned | dispatched | terminal | model-evaluable | correct | infrastructure missing | not-dispatched | incomplete | refusal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5120 | 2748 | 2753 | 2688 | 2331 | 60 | 2372 | 0 | 0 |

Primary matched families: 136 / 512. Operational success correct/dispatched: 84.825%; upper bound if unresolved dispatched outcomes all succeed: 84.862%.

Infrastructure missingness exceeds 1% of planned trials. Investigate causes and imbalance across conditions before drawing a definitive superiority conclusion.

Missing trials are not model errors. The sensitivity bounds below assign all non-evaluable planned comparison outcomes against / in favor of D.

## 4. Primary paired D−B comparison

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − generic_end | 136/512 | 5.742 | not_computed | 45 | not_computed |

Relative error reduction: 0.404 (NA at zero baseline error). Missingness sensitivity: [-45.410, 49.805] percentage points.

Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.

## 5. Five syntaxes: accuracy, truncation, and usage

| task | view | style | evaluable | accuracy | incomplete | input known | output known | reasoning known | cached known | tokens/correct | retry rate | mean latency ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | braces | 538 | 74.164% | 0 | 1667519 | 82001 | 77697 | 0 | NA | 0.000% | 13170.750 |
| scope_lookup | after_close | generic_end | 537 | 86.965% | 0 | 1684753 | 84192 | 79900 | 0 | NA | 0.000% | 10454.013 |
| scope_lookup | after_close | named_end | 536 | 91.231% | 0 | 1819401 | 61508 | 57236 | 0 | NA | 0.000% | 180093.727 |
| scope_lookup | after_close | padded_end | 538 | 92.379% | 0 | 1772584 | 85986 | 81682 | 0 | NA | 0.000% | 12918.067 |
| scope_lookup | after_close | typed_end | 539 | 88.868% | 0 | 1729025 | 76996 | 72686 | 0 | NA | 0.000% | 177012.254 |

Known tokens from unique final trial usage: 9063965; known generation-attempt tokens across retries: 9063965; trials without full input/output usage: 2432; generation attempts without full usage: 60.

HTTP attempts (count, generation, retry): 5503; known estimated cost USD: NA; cost-known trials: 0 / 5120. Estimates are not verified billing or free-quota balances.

Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.

## 6. Lexical regime, depth, and filler breakdown

Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.

| lexical | depth | filler | planned | evaluable | accuracy | infra missing |
| --- | --- | --- | --- | --- | --- | --- |
| natural | 2 | 0 | 160 | 95 | 81.053% | 5 |
| natural | 2 | 32 | 160 | 85 | 87.059% | 5 |
| natural | 2 | 128 | 160 | 65 | 93.846% | 0 |
| natural | 2 | 512 | 160 | 50 | 100.000% | 0 |
| natural | 4 | 0 | 160 | 100 | 56.000% | 0 |
| natural | 4 | 32 | 160 | 90 | 78.889% | 0 |
| natural | 4 | 128 | 160 | 90 | 88.889% | 5 |
| natural | 4 | 512 | 160 | 75 | 93.333% | 5 |
| natural | 8 | 0 | 160 | 70 | 82.857% | 0 |
| natural | 8 | 32 | 160 | 50 | 88.000% | 5 |
| natural | 8 | 128 | 160 | 105 | 91.429% | 0 |
| natural | 8 | 512 | 160 | 82 | 90.244% | 1 |
| natural | 16 | 0 | 160 | 105 | 90.476% | 0 |
| natural | 16 | 32 | 160 | 45 | 95.556% | 5 |
| natural | 16 | 128 | 160 | 76 | 93.421% | 7 |
| natural | 16 | 512 | 160 | 97 | 95.876% | 1 |
| nonce | 2 | 0 | 160 | 99 | 66.667% | 1 |
| nonce | 2 | 32 | 160 | 95 | 80.000% | 0 |
| nonce | 2 | 128 | 160 | 75 | 92.000% | 0 |
| nonce | 2 | 512 | 160 | 95 | 95.789% | 0 |
| nonce | 4 | 0 | 160 | 95 | 66.316% | 0 |
| nonce | 4 | 32 | 160 | 60 | 76.667% | 0 |
| nonce | 4 | 128 | 160 | 95 | 88.421% | 0 |
| nonce | 4 | 512 | 160 | 99 | 97.980% | 0 |
| nonce | 8 | 0 | 160 | 95 | 76.842% | 0 |
| nonce | 8 | 32 | 160 | 100 | 84.000% | 10 |
| nonce | 8 | 128 | 160 | 65 | 92.308% | 5 |
| nonce | 8 | 512 | 160 | 85 | 95.294% | 0 |
| nonce | 16 | 0 | 160 | 70 | 91.429% | 0 |
| nonce | 16 | 32 | 160 | 100 | 98.000% | 0 |
| nonce | 16 | 128 | 160 | 100 | 93.000% | 0 |
| nonce | 16 | 512 | 160 | 80 | 91.250% | 5 |

## 7. Exploratory paired comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − padded_end | 135/512 | -0.961 | not_computed | 36 | not_computed |
| scope_lookup | after_close | named_end − typed_end | 136/512 | 2.943 | not_computed | 32 | not_computed |
| scope_lookup | after_close | typed_end − generic_end | 137/512 | 2.919 | not_computed | 59 | not_computed |
| scope_lookup | after_close | named_end − braces | 136/512 | 14.688 | not_computed | 53 | not_computed |

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

**named_end**; trial `04443f9a40ad1d4283ba5072f6888df1b670bbc5cb0632f7e024a0707812bbb9`; outcome `wrong_answer`; gold `3037`.

Input:

Full input: [failure-inputs/04443f9a40ad1d4283ba5072f6888df1b670bbc5cb0632f7e024a0707812bbb9.txt](failure-inputs/04443f9a40ad1d4283ba5072f6888df1b670bbc5cb0632f7e024a0707812bbb9.txt). Excerpt lines 1–40 of 255:

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
The kind keywords are xaru for unit, telo for func, and gupi for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with nix KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of xaru, telo, gupi; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is sem VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is bov PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
sem x_b 4122
sem x_a 2890
telo n_xubbtrah
sem x_a 3834
bov p_gwsvasbv
nix telo n_xubbtrah
xaru n_svuhbhuc
sem x_a 9147
gupi n_vatidpaw
sem x_a 2381
bov p_bmncluwq
nix gupi n_vatidpaw
telo n_bitcouov
```

Raw answer:

```
4556
```

**generic_end**; trial `7bebacbb9c11837d4a983553c1de6c9f85d6907ac80f8367961b9fee075a11c1`; outcome `correct`; gold `3037`.

Input:

Full input: [failure-inputs/7bebacbb9c11837d4a983553c1de6c9f85d6907ac80f8367961b9fee075a11c1.txt](failure-inputs/7bebacbb9c11837d4a983553c1de6c9f85d6907ac80f8367961b9fee075a11c1.txt). Excerpt lines 1–40 of 255:

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
The kind keywords are xaru for unit, telo for func, and gupi for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with nix on its own line.
KIND is one of xaru, telo, gupi; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is sem VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is bov PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
sem x_b 4122
sem x_a 2890
telo n_xubbtrah
sem x_a 3834
bov p_gwsvasbv
nix
xaru n_svuhbhuc
sem x_a 9147
gupi n_vatidpaw
sem x_a 2381
bov p_bmncluwq
nix
telo n_bitcouov
```

Raw answer:

```
3037
```

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

**named_end**; trial `0ad1430561048a29e49cfdc6a20d77c810ae74e90cb627ac1cab8db0bd29e815`; outcome `wrong_answer`; gold `8902`.

Input:

Full input: [failure-inputs/0ad1430561048a29e49cfdc6a20d77c810ae74e90cb627ac1cab8db0bd29e815.txt](failure-inputs/0ad1430561048a29e49cfdc6a20d77c810ae74e90cb627ac1cab8db0bd29e815.txt). Excerpt lines 1–40 of 260:

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
8724
```

**generic_end**; trial `de7afb22252d9f38e0fb0f387ce96228a7da37cadd1e76e46b152480d08526b8`; outcome `correct`; gold `8902`.

Input:

Full input: [failure-inputs/de7afb22252d9f38e0fb0f387ce96228a7da37cadd1e76e46b152480d08526b8.txt](failure-inputs/de7afb22252d9f38e0fb0f387ce96228a7da37cadd1e76e46b152480d08526b8.txt). Excerpt lines 1–40 of 260:

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
8902
```

### both wrong

**named_end**; trial `334c9049b3b8c636ebc43235f1422b0ede1652f9b9638c0795b6584dcf13c5b0`; outcome `wrong_answer`; gold `9063`.

Input:

Full input: [failure-inputs/334c9049b3b8c636ebc43235f1422b0ede1652f9b9638c0795b6584dcf13c5b0.txt](failure-inputs/334c9049b3b8c636ebc43235f1422b0ede1652f9b9638c0795b6584dcf13c5b0.txt). Excerpt lines 1–40 of 291:

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
6717
```

**generic_end**; trial `7c00d860f22f8c92f6af0ed23c9585515d4c1574426e6fb020361c7c2748a7ff`; outcome `wrong_answer`; gold `9063`.

Input:

Full input: [failure-inputs/7c00d860f22f8c92f6af0ed23c9585515d4c1574426e6fb020361c7c2748a7ff.txt](failure-inputs/7c00d860f22f8c92f6af0ed23c9585515d4c1574426e6fb020361c7c2748a7ff.txt). Excerpt lines 1–40 of 291:

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
7670
```

**named_end**; trial `3b54e32a3e1e92bcb143ba1cf0f5bc14cdfb96d77fb33d252e3f78b5a1d613be`; outcome `wrong_answer`; gold `7941`.

Input:

Full input: [failure-inputs/3b54e32a3e1e92bcb143ba1cf0f5bc14cdfb96d77fb33d252e3f78b5a1d613be.txt](failure-inputs/3b54e32a3e1e92bcb143ba1cf0f5bc14cdfb96d77fb33d252e3f78b5a1d613be.txt). Excerpt lines 1–40 of 259:

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
8225
```

**generic_end**; trial `8dd693c4dbed2530ebc5814a5f2d8e30fd25ed2172ee7b9836a466806c3b5883`; outcome `wrong_answer`; gold `7941`.

Input:

Full input: [failure-inputs/8dd693c4dbed2530ebc5814a5f2d8e30fd25ed2172ee7b9836a466806c3b5883.txt](failure-inputs/8dd693c4dbed2530ebc5814a5f2d8e30fd25ed2172ee7b9836a466806c3b5883.txt). Excerpt lines 1–40 of 259:

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
8225
```

**named_end**; trial `650ede8347c52e77016bbed8d21ff30a3e1a2062fb6f3e28c7e25c3598cf5e42`; outcome `wrong_answer`; gold `5523`.

Input:

Full input: [failure-inputs/650ede8347c52e77016bbed8d21ff30a3e1a2062fb6f3e28c7e25c3598cf5e42.txt](failure-inputs/650ede8347c52e77016bbed8d21ff30a3e1a2062fb6f3e28c7e25c3598cf5e42.txt). Excerpt lines 1–40 of 259:

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
6985
```

**generic_end**; trial `e3981e266b48e668b4755de29bbf076c86a2ffe87ab1164a9c98d013e8950c0e`; outcome `wrong_answer`; gold `5523`.

Input:

Full input: [failure-inputs/e3981e266b48e668b4755de29bbf076c86a2ffe87ab1164a9c98d013e8950c0e.txt](failure-inputs/e3981e266b48e668b4755de29bbf076c86a2ffe87ab1164a9c98d013e8950c0e.txt). Excerpt lines 1–40 of 259:

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
2324
```


## 10. Limitations and next experiments

The benchmark tests explained miniature grammars plus fixed few-shot examples, not grammar learning from examples alone. The eight fixed nonce vocabularies do not represent every artificial vocabulary. Indentation is non-semantic; D/E character-length matching is not token matching.

Accuracy, token usage, reasoning usage, caching, and latency are observable task and service outcomes; they do not directly measure cognitive load, dependency distance inside a model, or FLOPs. First parse errors are not counts of every syntax error. Self-contained fences are auxiliary normalization only.

An observed zero or undetected difference does not prove general equivalence. Differences can reflect retrieval cues, delimiter identification, tokenization, or prompt length. Missingness, ceilings/floors, generation output burden, and method-specific measurement limits constrain causal explanations.

Follow-up candidates after a new preregistered holdout: indentation, same-kind nesting, number of examples, reasoning effort, repair tasks, local-window cuts, and input-token matching. Do not remove failures or stop when an interval first becomes favorable.
