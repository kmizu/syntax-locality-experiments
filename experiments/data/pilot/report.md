# Syntax locality ICL experiment report

## 1. Run status and summary

Status: **all planned trials terminal**. synthetic_mock=false.

Strict model-evaluable accuracy: 90.111%; observed primary D−B: 8.333 percentage points.

## 2. Experiment conditions

Preset: pilot; phase: p1; model: `gpt-5.6-terra`; started: 2026-10-05T15:38:57.662680200Z; protocol version: 1; protocol hash: `f7dd15dd3c44b507506e123517157926a35a53b52c1d4aab278664f512a1e182`.

Bootstrap seed: 20261005; iterations when eligible: 10,000; unit: structural family; strata: depth × filler; lexical regimes: natural, nonce.

```
{"bootstrapSeed":20261005,"concurrency":4,"depths":[2,4,8],"fewShotCount":8,"fillersGeneration":[0,8,32],"fillersReading":[0,32,128],"generatorVersion":"1","masterSeed":20261005,"maxOutputTokensGeneration":16384,"maxOutputTokensReading":8192,"maxRpm":6E+1,"maxTpm":2E+5,"model":"gpt-5.6-terra","promptVersion":"1","reasoningEffort":"low","replicates":1,"scorerVersion":"1","seedsPerCell":4,"sourceHash":"f3e5a0a43c3ca7332be53f098c2b8d9064360312d6e7d5efc16cc4a74fd646ab"}
```

## 3. Planned / executed counts and missingness

| planned | dispatched | terminal | model-evaluable | correct | infrastructure missing | not-dispatched | incomplete | refusal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1800 | 1800 | 1800 | 1800 | 1622 | 0 | 0 | 0 | 0 |

Primary matched families: 36 / 36. Operational success correct/dispatched: 90.111%; upper bound if unresolved dispatched outcomes all succeed: 90.111%.

Missing trials are not model errors. The sensitivity bounds below assign all non-evaluable planned comparison outcomes against / in favor of D.

## 4. Primary paired D−B comparison

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| scope_lookup | after_close | named_end − generic_end | 36/36 | 8.333 | [0.000, 16.667] | 13 | computed |

Relative error reduction: 0.500 (NA at zero baseline error). Missingness sensitivity: [8.333, 8.333] percentage points.

Replicates are averaged within family and lexical regime, regimes have equal weight, and depth × filler cells have equal weight. A wholly missing planned cell leaves the overall effect undefined. Exact McNemar values, where available for single-regime unaveraged binary family pairs, are descriptive.

## 5. Five syntaxes: accuracy, truncation, and usage

| task | view | style | evaluable | accuracy | incomplete | input known | output known | reasoning known | cached known | tokens/correct | retry rate | mean latency ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| active_stack | after_close | braces | 72 | 65.278% | 0 | 160081 | 6483 | 4840 | 0 | 3543.915 | 0.000% | 8783.694 |
| active_stack | after_close | generic_end | 72 | 87.500% | 0 | 163080 | 6178 | 4575 | 0 | 2686.635 | 0.000% | 7650.556 |
| active_stack | after_close | named_end | 72 | 97.222% | 0 | 181298 | 4655 | 3028 | 0 | 2656.471 | 0.000% | 10506.556 |
| active_stack | after_close | padded_end | 72 | 95.833% | 0 | 174120 | 5745 | 4131 | 0 | 2606.739 | 0.000% | 9573.667 |
| active_stack | after_close | typed_end | 72 | 91.667% | 0 | 167222 | 5570 | 3948 | 0 | 2618.061 | 0.000% | 9009.375 |
| active_stack | before_close | braces | 72 | 98.611% | 0 | 165095 | 5004 | 2310 | 0 | 2395.761 | 0.000% | 8807.569 |
| active_stack | before_close | generic_end | 72 | 100.000% | 0 | 166683 | 5599 | 2889 | 0 | 2392.806 | 0.000% | 9161.014 |
| active_stack | before_close | named_end | 72 | 100.000% | 0 | 177061 | 4982 | 2286 | 0 | 2528.375 | 0.000% | 9865.028 |
| active_stack | before_close | padded_end | 72 | 98.611% | 0 | 173307 | 5834 | 3122 | 0 | 2523.113 | 0.000% | 9738.236 |
| active_stack | before_close | typed_end | 72 | 98.611% | 0 | 169257 | 5066 | 2369 | 0 | 2455.254 | 0.000% | 8516.139 |
| ast_to_source | complete | braces | 72 | 80.556% | 0 | 513072 | 23092 | 5763 | 0 | 9244.207 | 0.000% | 21356.472 |
| ast_to_source | complete | generic_end | 72 | 63.889% | 0 | 516512 | 22972 | 5186 | 0 | 11727.913 | 0.000% | 20980.542 |
| ast_to_source | complete | named_end | 72 | 98.611% | 0 | 537588 | 26827 | 5552 | 0 | 7949.507 | 0.000% | 21198.333 |
| ast_to_source | complete | padded_end | 72 | 68.056% | 0 | 529184 | 26219 | 6672 | 0 | 11334.755 | 0.000% | 20801.194 |
| ast_to_source | complete | typed_end | 72 | 83.333% | 0 | 521244 | 24003 | 5491 | 0 | 9087.450 | 0.000% | 23516.375 |
| scope_lookup | after_close | braces | 72 | 72.222% | 0 | 154105 | 7510 | 6934 | 0 | 3107.981 | 0.000% | 9006.403 |
| scope_lookup | after_close | generic_end | 72 | 83.333% | 0 | 157104 | 7893 | 7317 | 0 | 2749.950 | 0.000% | 10000.278 |
| scope_lookup | after_close | named_end | 72 | 91.667% | 0 | 174604 | 5619 | 5047 | 0 | 2730.652 | 0.000% | 9494.806 |
| scope_lookup | after_close | padded_end | 72 | 91.667% | 0 | 168144 | 8122 | 7546 | 0 | 2670.697 | 0.000% | 9607.847 |
| scope_lookup | after_close | typed_end | 72 | 86.111% | 0 | 161248 | 7474 | 6900 | 0 | 2721.323 | 0.000% | 10260.083 |
| scope_lookup | before_close | braces | 72 | 100.000% | 0 | 153143 | 2224 | 1692 | 0 | 2157.875 | 0.000% | 9054.875 |
| scope_lookup | before_close | generic_end | 72 | 100.000% | 0 | 154731 | 2650 | 2126 | 0 | 2185.847 | 0.000% | 9645.847 |
| scope_lookup | before_close | named_end | 72 | 100.000% | 0 | 164601 | 1881 | 1373 | 0 | 2312.250 | 0.000% | 9930.125 |
| scope_lookup | before_close | padded_end | 72 | 100.000% | 0 | 161355 | 3515 | 2963 | 0 | 2289.861 | 0.000% | 9007.292 |
| scope_lookup | before_close | typed_end | 72 | 100.000% | 0 | 157301 | 2629 | 2101 | 0 | 2221.250 | 0.000% | 9370.042 |

Known tokens from unique final trial usage: 6148886; known generation-attempt tokens across retries: 6148886; trials without full input/output usage: 0; generation attempts without full usage: 0.

HTTP attempts (count, generation, retry): 3600; known estimated cost USD: NA; cost-known trials: 0 / 1800. Estimates are not verified billing or free-quota balances.

Unknown usage is excluded from known sums and explicitly counted, never replaced with a zero estimate. Cost ratios are NA when usage is incomplete or there are no correct answers. summary.csv separately reports unique-trial and all-generation-attempt tokens per correct result. Non-reasoning output is output minus reasoning only where both fields exist; it is not an exact answer-text token count. Retried and non-retried latency summaries are in summary.csv.

## 6. Lexical regime, depth, and filler breakdown

Full task/view/style/natural-or-nonce/depth/filler counts, usage completeness, and outcomes are in [summary.csv](summary.csv). Each row preserves its original trial denominator.

| lexical | depth | filler | planned | evaluable | accuracy | infra missing |
| --- | --- | --- | --- | --- | --- | --- |
| natural | 2 | 0 | 100 | 100 | 95.000% | 0 |
| natural | 2 | 8 | 20 | 20 | 100.000% | 0 |
| natural | 2 | 32 | 100 | 100 | 99.000% | 0 |
| natural | 2 | 128 | 80 | 80 | 97.500% | 0 |
| natural | 4 | 0 | 100 | 100 | 86.000% | 0 |
| natural | 4 | 8 | 20 | 20 | 100.000% | 0 |
| natural | 4 | 32 | 100 | 100 | 97.000% | 0 |
| natural | 4 | 128 | 80 | 80 | 95.000% | 0 |
| natural | 8 | 0 | 100 | 100 | 79.000% | 0 |
| natural | 8 | 8 | 20 | 20 | 40.000% | 0 |
| natural | 8 | 32 | 100 | 100 | 79.000% | 0 |
| natural | 8 | 128 | 80 | 80 | 96.250% | 0 |
| nonce | 2 | 0 | 100 | 100 | 91.000% | 0 |
| nonce | 2 | 8 | 20 | 20 | 100.000% | 0 |
| nonce | 2 | 32 | 100 | 100 | 95.000% | 0 |
| nonce | 2 | 128 | 80 | 80 | 96.250% | 0 |
| nonce | 4 | 0 | 100 | 100 | 87.000% | 0 |
| nonce | 4 | 8 | 20 | 20 | 100.000% | 0 |
| nonce | 4 | 32 | 100 | 100 | 93.000% | 0 |
| nonce | 4 | 128 | 80 | 80 | 97.500% | 0 |
| nonce | 8 | 0 | 100 | 100 | 78.000% | 0 |
| nonce | 8 | 8 | 20 | 20 | 40.000% | 0 |
| nonce | 8 | 32 | 100 | 100 | 85.000% | 0 |
| nonce | 8 | 128 | 80 | 80 | 95.000% | 0 |

## 7. Exploratory paired comparisons

| task | view | contrast | families | effect pp | 95% CI pp | discordant | CI status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| active_stack | after_close | named_end − generic_end | 36/36 | 9.722 | [1.389, 19.444] | 8 | computed |
| active_stack | after_close | named_end − padded_end | 36/36 | 1.389 | [-4.167, 6.944] | 5 | computed |
| active_stack | after_close | named_end − typed_end | 36/36 | 5.556 | [-1.389, 12.500] | 7 | computed |
| active_stack | after_close | typed_end − generic_end | 36/36 | 4.167 | [-5.556, 13.889] | 10 | computed |
| active_stack | after_close | named_end − braces | 36/36 | 31.944 | [22.222, 41.667] | 20 | computed |
| active_stack | before_close | named_end − generic_end | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| active_stack | before_close | named_end − padded_end | 36/36 | 1.389 | [0.000, 4.167] | 1 | computed |
| active_stack | before_close | named_end − typed_end | 36/36 | 1.389 | [0.000, 4.167] | 1 | computed |
| active_stack | before_close | typed_end − generic_end | 36/36 | -1.389 | [-4.167, 0.000] | 1 | computed |
| active_stack | before_close | named_end − braces | 36/36 | 1.389 | [0.000, 4.167] | 1 | computed |
| ast_to_source | complete | named_end − generic_end | 36/36 | 34.722 | [30.556, 37.500] | 15 | computed |
| ast_to_source | complete | named_end − padded_end | 36/36 | 30.556 | [26.389, 33.333] | 13 | computed |
| ast_to_source | complete | named_end − typed_end | 36/36 | 15.278 | [6.944, 23.611] | 8 | computed |
| ast_to_source | complete | typed_end − generic_end | 36/36 | 19.444 | [11.111, 27.778] | 9 | computed |
| ast_to_source | complete | named_end − braces | 36/36 | 18.056 | [11.111, 25.000] | 11 | computed |
| scope_lookup | after_close | named_end − padded_end | 36/36 | 0.000 | [-6.944, 6.944] | 11 | computed |
| scope_lookup | after_close | named_end − typed_end | 36/36 | 5.556 | [-1.389, 12.500] | 10 | computed |
| scope_lookup | after_close | typed_end − generic_end | 36/36 | 2.778 | [-6.944, 12.500] | 16 | computed |
| scope_lookup | after_close | named_end − braces | 36/36 | 19.444 | [11.111, 27.778] | 19 | computed |
| scope_lookup | before_close | named_end − generic_end | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| scope_lookup | before_close | named_end − padded_end | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| scope_lookup | before_close | named_end − typed_end | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| scope_lookup | before_close | typed_end − generic_end | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |
| scope_lookup | before_close | named_end − braces | 36/36 | 0.000 | [0.000, 0.000] | 0 | degenerate |

All D−E, D−C, C−B, A contrasts, other tasks/views, and pilot comparisons are exploratory unless a separate testing plan was frozen. See [paired-comparisons.csv](paired-comparisons.csv).

## 8. Reading and generation

Tasks actually planned in this saved run: active_stack, ast_to_source, scope_lookup.

Generation is assessed independently by strict syntax and exact AST equality. Its shorter capped AST bodies and longer correct D/E outputs differ from long-prefix reading tasks.

Compare task-specific effects and output tokens in the tables; a reading improvement does not imply a generation improvement.

## 9. Representative failures

### D correct / B wrong

**named_end**; trial `0253ae25a5fac77981543ba697d29ceacc58f1bee6bbee8ee0a89000d5129a78`; outcome `correct`; gold `sem x_a 3954
xaru n_jophhaxp
sem x_a 1203
bov p_khmnbexd
nix xaru n_jophhaxp
telo n_uvnsdvzv
sem x_a 6013
gupi n_xwoqzjhg
sem x_a 9153
gupi n_mdrjsahq
sem x_a 9579
gupi n_aykcscfa
sem x_a 8566
bov p_hjhqhuxp
nix gupi n_aykcscfa
telo n_vqaesauu
gupi n_vdtdbtkl
sem x_a 2063
gupi n_nwgcuwac
sem x_a 8934
telo n_dxfqogdj
sem x_a 4165
gupi n_nifumeyt
sem x_a 1548
bov p_ecwghzio
bov p_grlfquxl
bov p_hdxmjzyu
bov p_cgmuypdc
bov p_tmzqgvsw
bov p_wcwfsliv
bov p_lighdulc
bov p_dgxsmgsx
nix gupi n_nifumeyt
nix telo n_dxfqogdj
nix gupi n_nwgcuwac
nix gupi n_vdtdbtkl
nix telo n_vqaesauu
nix gupi n_mdrjsahq
nix gupi n_xwoqzjhg
nix telo n_uvnsdvzv
`.

Input:

Full input: [failure-inputs/0253ae25a5fac77981543ba697d29ceacc58f1bee6bbee8ee0a89000d5129a78.txt](failure-inputs/0253ae25a5fac77981543ba697d29ceacc58f1bee6bbee8ee0a89000d5129a78.txt). Excerpt lines 1–40 of 414:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
Translate the neutral AST table into a complete program in the following scope language.
Every block, regardless of its kind, creates a local scope.
ROOT is the implicit global scope and produces no source line. SCOPE opens a block and must be closed after its children.
LET binds its variable to its value. NOP produces a no-effect statement with its payload.
Each node's parent identifies its containing body. The order field gives the statement order within that body.
Rows are in parent-before-child order; their presentation order does not give statement order.
Node IDs are references for the table only and must not appear in source.
Use the grammar's keywords for the table's semantic kinds unit, func, and area.
Return only the complete program, without Markdown fences or explanation.

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
AST table:
node	parent	order	tag	kind	name	variable	value	payload
d_sdlnxqwjbicy	-	0	ROOT	-	-	-	-	-
d_gqnvronnrskf	d_sdlnxqwjbicy	3	SCOPE	area	n_qzepptcj	-	-	-
d_cvxbsdvsarlb	d_sdlnxqwjbicy	1	LET	-	-	x_a	2330	-
d_edripeuobvff	d_gqnvronnrskf	0	LET	-	-	x_a	4620	-
d_yahwzpqqzpjb	d_gqnvronnrskf	1	SCOPE	area	n_awmttwpc	-	-	-
d_pmxsavdddyzx	d_sdlnxqwjbicy	0	LET	-	-	x_b	2242	-
d_ffttnisngkfp	d_gqnvronnrskf	2	SCOPE	unit	n_khtbxnwa	-	-	-
d_wskkoplbppjr	d_ffttnisngkfp	0	LET	-	-	x_a	6882	-
d_sboygrtdevbi	d_sdlnxqwjbicy	2	SCOPE	func	n_hinpuiyv	-	-	-
d_ijmmfdntskdr	d_yahwzpqqzpjb	1	NOP	-	-	-	-	p_uiwkqwzr
d_vvztrxlmvuwm	d_sboygrtdevbi	0	LET	-	-	x_a	6531	-
d_slrwedhndnqt	d_yahwzpqqzpjb	0	LET	-	-	x_a	8830	-
d_rinkgwoedvwb	d_sboygrtdevbi	1	NOP	-	-	-	-	p_kroyqmmk
Program:
```

Raw answer:

```
sem x_a 3954
xaru n_jophhaxp
sem x_a 1203
bov p_khmnbexd
nix xaru n_jophhaxp
telo n_uvnsdvzv
sem x_a 6013
gupi n_xwoqzjhg
sem x_a 9153
gupi n_mdrjsahq
sem x_a 9579
gupi n_aykcscfa
sem x_a 8566
bov p_hjhqhuxp
nix gupi n_aykcscfa
telo n_vqaesauu
gupi n_vdtdbtkl
sem x_a 2063
gupi n_nwgcuwac
sem x_a 8934
telo n_dxfqogdj
sem x_a 4165
gupi n_nifumeyt
sem x_a 1548
bov p_ecwghzio
bov p_grlfquxl
bov p_hdxmjzyu
bov p_cgmuypdc
bov p_tmzqgvsw
bov p_wcwfsliv
bov p_lighdulc
bov p_dgxsmgsx
nix gupi n_nifumeyt
nix telo n_dxfqogdj
nix gupi n_nwgcuwac
nix gupi n_vdtdbtkl
nix telo n_vqaesauu
nix gupi n_mdrjsahq
nix gupi n_xwoqzjhg
nix telo n_uvnsdvzv
```

**generic_end**; trial `dc9fddaa1e2a1ec4c4254ac1c3b1bafb3aecf99d0b3e26a1dd66cc0b28beb62b`; outcome `invalid_generated_syntax`; gold `sem x_a 3954
xaru n_jophhaxp
sem x_a 1203
bov p_khmnbexd
nix
telo n_uvnsdvzv
sem x_a 6013
gupi n_xwoqzjhg
sem x_a 9153
gupi n_mdrjsahq
sem x_a 9579
gupi n_aykcscfa
sem x_a 8566
bov p_hjhqhuxp
nix
telo n_vqaesauu
gupi n_vdtdbtkl
sem x_a 2063
gupi n_nwgcuwac
sem x_a 8934
telo n_dxfqogdj
sem x_a 4165
gupi n_nifumeyt
sem x_a 1548
bov p_ecwghzio
bov p_grlfquxl
bov p_hdxmjzyu
bov p_cgmuypdc
bov p_tmzqgvsw
bov p_wcwfsliv
bov p_lighdulc
bov p_dgxsmgsx
nix
nix
nix
nix
nix
nix
nix
nix
`.

Input:

Full input: [failure-inputs/dc9fddaa1e2a1ec4c4254ac1c3b1bafb3aecf99d0b3e26a1dd66cc0b28beb62b.txt](failure-inputs/dc9fddaa1e2a1ec4c4254ac1c3b1bafb3aecf99d0b3e26a1dd66cc0b28beb62b.txt). Excerpt lines 1–40 of 414:

```
Follow the task and return only the requested answer, without explanation or Markdown fences.
Translate the neutral AST table into a complete program in the following scope language.
Every block, regardless of its kind, creates a local scope.
ROOT is the implicit global scope and produces no source line. SCOPE opens a block and must be closed after its children.
LET binds its variable to its value. NOP produces a no-effect statement with its payload.
Each node's parent identifies its containing body. The order field gives the statement order within that body.
Rows are in parent-before-child order; their presentation order does not give statement order.
Node IDs are references for the table only and must not appear in source.
Use the grammar's keywords for the table's semantic kinds unit, func, and area.
Return only the complete program, without Markdown fences or explanation.

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
AST table:
node	parent	order	tag	kind	name	variable	value	payload
d_sdlnxqwjbicy	-	0	ROOT	-	-	-	-	-
d_gqnvronnrskf	d_sdlnxqwjbicy	3	SCOPE	area	n_qzepptcj	-	-	-
d_cvxbsdvsarlb	d_sdlnxqwjbicy	1	LET	-	-	x_a	2330	-
d_edripeuobvff	d_gqnvronnrskf	0	LET	-	-	x_a	4620	-
d_yahwzpqqzpjb	d_gqnvronnrskf	1	SCOPE	area	n_awmttwpc	-	-	-
d_pmxsavdddyzx	d_sdlnxqwjbicy	0	LET	-	-	x_b	2242	-
d_ffttnisngkfp	d_gqnvronnrskf	2	SCOPE	unit	n_khtbxnwa	-	-	-
d_wskkoplbppjr	d_ffttnisngkfp	0	LET	-	-	x_a	6882	-
d_sboygrtdevbi	d_sdlnxqwjbicy	2	SCOPE	func	n_hinpuiyv	-	-	-
d_ijmmfdntskdr	d_yahwzpqqzpjb	1	NOP	-	-	-	-	p_uiwkqwzr
d_vvztrxlmvuwm	d_sboygrtdevbi	0	LET	-	-	x_a	6531	-
d_slrwedhndnqt	d_yahwzpqqzpjb	0	LET	-	-	x_a	8830	-
d_rinkgwoedvwb	d_sboygrtdevbi	1	NOP	-	-	-	-	p_kroyqmmk
Program:
```

Raw answer:

```
sem x_a 3954
xaru n_jophhaxp
sem x_a 1203
bov p_khmnbexd
nix
telo n_uvnsdvzv
sem x_a 6013
gupi n_xwoqzjhg
sem x_a 9153
gupi n_mdrjsahq
sem x_a 9579
gupi n_aykcscfa
sem x_a 8566
bov p_hjhqhuxp
nix
telo n_vqaesauu
gupi n_vdtdbtkl
sem x_a 2063
gupi n_nwgcuwac
sem x_a 8934
telo n_dxfqogdj
sem x_a 4165
gupi n_nifumeyt
sem x_a 1548
bov p_ecwghzio
bov p_grlfquxl
bov p_hdxmjzyu
bov p_cgmuypdc
bov p_tmzqgvsw
bov p_wcwfsliv
bov p_lighdulc
bov p_dgxsmgsx
nix
nix
nix
nix
nix
nix
```

**named_end**; trial `03b49d5d7bf2142b054e0347240505244501935f129596ea494f0dcd86e809c9`; outcome `correct`; gold `["n_izbpetmz","n_ejlmawlq","n_jlyjogws","n_pswivkws"]`.

Input:

Full input: [failure-inputs/03b49d5d7bf2142b054e0347240505244501935f129596ea494f0dcd86e809c9.txt](failure-inputs/03b49d5d7bf2142b054e0347240505244501935f129596ea494f0dcd86e809c9.txt). Excerpt lines 1–40 of 270:

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
Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].

SYNTAX
The kind keywords are nezu for unit, sapu for func, and jomi for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with wor KIND NAME on one line. KIND and NAME must repeat that block's opening keyword and name.
KIND is one of nezu, sapu, jomi; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is fal VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is kiv PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
fal x_b 8303
fal x_a 8323
nezu n_qflwqmaz
fal x_a 8435
kiv p_zylqsrnu
wor nezu n_qflwqmaz
sapu n_myndeesr
fal x_a 8749
jomi n_inuktfag
fal x_a 8459
kiv p_mjpldyqb
wor jomi n_inuktfag
jomi n_gqjdgjnv
```

Raw answer:

```
["n_izbpetmz","n_ejlmawlq","n_jlyjogws","n_pswivkws"]
```

**generic_end**; trial `8a46678e19e834405bdf00a2a6eb07fbad7ee91d5f03ebf26fbbbb68f0aca8aa`; outcome `wrong_answer`; gold `["n_izbpetmz","n_ejlmawlq","n_jlyjogws","n_pswivkws"]`.

Input:

Full input: [failure-inputs/8a46678e19e834405bdf00a2a6eb07fbad7ee91d5f03ebf26fbbbb68f0aca8aa.txt](failure-inputs/8a46678e19e834405bdf00a2a6eb07fbad7ee91d5f03ebf26fbbbb68f0aca8aa.txt). Excerpt lines 1–40 of 270:

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
Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].

SYNTAX
The kind keywords are nezu for unit, sapu for func, and jomi for area. Each creates a local scope.
Open a block with KIND NAME on one line.
Close the innermost open block with wor on its own line.
KIND is one of nezu, sapu, jomi; NAME is n_ followed by exactly eight lowercase letters and is unique in the program.
A binding statement (let) is fal VARIABLE VALUE. VARIABLE is x_ followed by one letter a through f; VALUE is a four digit integer from 1000 through 9999.
Within one scope a variable is bound at most once. Inner bindings hide outer bindings until their scope closes.
A no-effect statement (nop) is kiv PAYLOAD. PAYLOAD is p_ followed by eight lowercase letters.
The observation marker is probe PROBE_ID VARIABLE. PROBE_ID is q_ followed by eight lowercase letters.
Use one statement per line. Indentation has no meaning; scope is determined solely by explicit opening and closing lines.

EXAMPLES
Example 1
Program prefix:
fal x_b 8303
fal x_a 8323
nezu n_qflwqmaz
fal x_a 8435
kiv p_zylqsrnu
wor
sapu n_myndeesr
fal x_a 8749
jomi n_inuktfag
fal x_a 8459
kiv p_mjpldyqb
wor
jomi n_gqjdgjnv
```

Raw answer:

```
["n_izbpetmz","n_ejlmawlq","n_jlyjogws"]
```

**named_end**; trial `03bd1c4159c7ad8d71a7b43ce0e3c3870e30984a8167d90d2d2c9af362850dea`; outcome `correct`; gold `6402`.

Input:

Full input: [failure-inputs/03bd1c4159c7ad8d71a7b43ce0e3c3870e30984a8167d90d2d2c9af362850dea.txt](failure-inputs/03bd1c4159c7ad8d71a7b43ce0e3c3870e30984a8167d90d2d2c9af362850dea.txt). Excerpt lines 1–40 of 254:

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
6402
```

**generic_end**; trial `9720b3fd95342aa2b0a7ad14555cfa617cffcc7b7852f99476f7087e8f855293`; outcome `wrong_answer`; gold `6402`.

Input:

Full input: [failure-inputs/9720b3fd95342aa2b0a7ad14555cfa617cffcc7b7852f99476f7087e8f855293.txt](failure-inputs/9720b3fd95342aa2b0a7ad14555cfa617cffcc7b7852f99476f7087e8f855293.txt). Excerpt lines 1–40 of 254:

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
8162
```

### D wrong / B correct

**named_end**; trial `3965c4647c2e5db75d28c30a2a9809fbbaf71052bb8af7f688fe80e18fecc6a1`; outcome `wrong_answer`; gold `8636`.

Input:

Full input: [failure-inputs/3965c4647c2e5db75d28c30a2a9809fbbaf71052bb8af7f688fe80e18fecc6a1.txt](failure-inputs/3965c4647c2e5db75d28c30a2a9809fbbaf71052bb8af7f688fe80e18fecc6a1.txt). Excerpt lines 1–40 of 397:

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
6050
```

**generic_end**; trial `489fb48bce73d9d390d64c516360b71c3679a8be1b7bc2a31e601b9e6fb3065f`; outcome `correct`; gold `8636`.

Input:

Full input: [failure-inputs/489fb48bce73d9d390d64c516360b71c3679a8be1b7bc2a31e601b9e6fb3065f.txt](failure-inputs/489fb48bce73d9d390d64c516360b71c3679a8be1b7bc2a31e601b9e6fb3065f.txt). Excerpt lines 1–40 of 397:

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
8636
```

**named_end**; trial `8f467ecbdaa4c240c3a1d64692d6d4d580ee7e924b3f0a715948f407182efbc0`; outcome `wrong_answer`; gold `["n_tiysyemf","n_ctnihjws","n_ntxboeqs","n_ginlueac"]`.

Input:

Full input: [failure-inputs/8f467ecbdaa4c240c3a1d64692d6d4d580ee7e924b3f0a715948f407182efbc0.txt](failure-inputs/8f467ecbdaa4c240c3a1d64692d6d4d580ee7e924b3f0a715948f407182efbc0.txt). Excerpt lines 1–40 of 301:

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
Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].

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
let x_b 8303
let x_a 8323
unit n_qflwqmaz
let x_a 8435
nop p_zylqsrnu
end unit n_qflwqmaz
func n_myndeesr
let x_a 8749
area n_inuktfag
let x_a 8459
nop p_mjpldyqb
end area n_inuktfag
area n_gqjdgjnv
```

Raw answer:

```
["n_tiysyemf","n_ctnihjws","n_ntxboeqs","n_ginlueac","n_nyvdugjb"]
```

**generic_end**; trial `589c14a78eb69d58a34cdab6be3118c578358f3a9e786d93501b7f14c72a22a1`; outcome `correct`; gold `["n_tiysyemf","n_ctnihjws","n_ntxboeqs","n_ginlueac"]`.

Input:

Full input: [failure-inputs/589c14a78eb69d58a34cdab6be3118c578358f3a9e786d93501b7f14c72a22a1.txt](failure-inputs/589c14a78eb69d58a34cdab6be3118c578358f3a9e786d93501b7f14c72a22a1.txt). Excerpt lines 1–40 of 301:

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
Return only a JSON array of the names of still-open blocks, ordered from outermost to innermost. Do not include the implicit global scope. If no blocks remain open, return [].

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
let x_b 8303
let x_a 8323
unit n_qflwqmaz
let x_a 8435
nop p_zylqsrnu
end
func n_myndeesr
let x_a 8749
area n_inuktfag
let x_a 8459
nop p_mjpldyqb
end
area n_gqjdgjnv
```

Raw answer:

```
["n_tiysyemf","n_ctnihjws","n_ntxboeqs","n_ginlueac"]
```

**named_end**; trial `99ef9ebd39dd6b1a67142be53b9b4dcbcf6edeaa119797e9f939367382ed9977`; outcome `wrong_answer`; gold `4270`.

Input:

Full input: [failure-inputs/99ef9ebd39dd6b1a67142be53b9b4dcbcf6edeaa119797e9f939367382ed9977.txt](failure-inputs/99ef9ebd39dd6b1a67142be53b9b4dcbcf6edeaa119797e9f939367382ed9977.txt). Excerpt lines 1–40 of 259:

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
2466
```

**generic_end**; trial `adb41f8e1022976414cb3c6b890bc974bf23ffe2c2fb3b03a36679a878de5550`; outcome `correct`; gold `4270`.

Input:

Full input: [failure-inputs/adb41f8e1022976414cb3c6b890bc974bf23ffe2c2fb3b03a36679a878de5550.txt](failure-inputs/adb41f8e1022976414cb3c6b890bc974bf23ffe2c2fb3b03a36679a878de5550.txt). Excerpt lines 1–40 of 259:

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
4270
```

### both wrong

**named_end**; trial `de8b696062a7b413556e0a8f2d6f3f9ecb0bc917b0ad1c629b7de78e5478d3e3`; outcome `wrong_answer`; gold `3328`.

Input:

Full input: [failure-inputs/de8b696062a7b413556e0a8f2d6f3f9ecb0bc917b0ad1c629b7de78e5478d3e3.txt](failure-inputs/de8b696062a7b413556e0a8f2d6f3f9ecb0bc917b0ad1c629b7de78e5478d3e3.txt). Excerpt lines 1–40 of 259:

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
6426
```

**generic_end**; trial `31b44cc1f40ee9d5dd36bf336964056f79efebf300b542e7d039ef69f8163201`; outcome `wrong_answer`; gold `3328`.

Input:

Full input: [failure-inputs/31b44cc1f40ee9d5dd36bf336964056f79efebf300b542e7d039ef69f8163201.txt](failure-inputs/31b44cc1f40ee9d5dd36bf336964056f79efebf300b542e7d039ef69f8163201.txt). Excerpt lines 1–40 of 259:

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
6426
```


## 10. Limitations and next experiments

The benchmark tests explained miniature grammars plus fixed few-shot examples, not grammar learning from examples alone. The eight fixed nonce vocabularies do not represent every artificial vocabulary. Indentation is non-semantic; D/E character-length matching is not token matching.

Accuracy, token usage, reasoning usage, caching, and latency are observable task and service outcomes; they do not directly measure cognitive load, dependency distance inside a model, or FLOPs. First parse errors are not counts of every syntax error. Self-contained fences are auxiliary normalization only.

An observed zero or undetected difference does not prove general equivalence. Differences can reflect retrieval cues, delimiter identification, tokenization, or prompt length. Missingness, ceilings/floors, generation output burden, and method-specific measurement limits constrain causal explanations.

Follow-up candidates after a new preregistered holdout: indentation, same-kind nesting, number of examples, reasoning effort, repair tasks, local-window cuts, and input-token matching. Do not remove failures or stop when an interval first becomes favorable.
