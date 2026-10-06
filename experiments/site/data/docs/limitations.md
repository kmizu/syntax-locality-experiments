# Interpretation limits

This experiment compares five syntaxes for identical generated scope structures. The result is conditional on the model, prompt, examples, fixed lexical mappings, difficulty grid, output caps, and sampled families. Improvement, no detectable difference, and deterioration are equally valid outcomes.

Reading and generation ask different questions. A named label is available **after it has been read**; writing that label still requires reproducing a distant opener/name. D/E require longer correct generated sources than B. Generation's capped depth/filler and neutral table input are not the same difficulty as a long reading prefix; the table also contains structural cues.

The nonce condition weakens familiar-keyword explanations but cannot remove pretraining, tokenization, or resemblance to existing syntax. Only eight fixed nonce sets are tested. D/E closing lines match character length in the baseline condition, not exact token count. E uses a fixed suffix in both lexical regimes. Before-close is a control view, not a guaranteed zero-effect negative control.

Do not translate accuracy, input/output/reasoning/cached tokens, or latency into an internal cognitive-load/FLOP measurement. Network/load/caching/output length/retry affect latency. Cached input usage is not evidence that a model learned prior answers. Token estimates, local accounting, published shared allowance, and actual billing are separate quantities.

The structural family is the independent sampling unit. Five styles, two lexical regimes, nonce assignments, and repeated generations are not additional independent AST samples. Stratified family bootstrap uncertainty applies to available matched families and does not automatically generalize to arbitrary artificial grammars/vocabularies. Inspect missingness by condition, especially beyond 1% in main, and retain worst/best sensitivity bounds.

A degenerate interval or zero observed difference does not establish strict population equality. Inspect discordance and ceiling/floor effects; expand difficulty only through a new frozen holdout. An improvement is consistent with several mechanisms—search/retrieval cues, clearer delimiters, attention, or tokenization—not a uniquely established shortening of an internal dependency.

Strict scores preserve the task contract. Prose/fences are main-score format violations; a separately labeled one-fence auxiliary score does not replace strict accuracy. Incomplete/refusal results are model failures. API/transport/ambiguous/decode outcomes are missing, and unexecuted trials are not errors. The first parser rejection is one located error, not a census of every syntax defect in an answer.

Finite round-trip/oracle/metamorphic tests establish tested behavior, not a mathematical proof of all-input correctness. Synthetic fixtures establish plumbing, not actual model quality. Capability preflight establishes that a tiny request worked with recorded settings, not that smoke/pilot/main ran. A complete implementation does not itself support a grammar-advantage hypothesis.

Potential later experiments include indentation, example count, same-kind nesting, reasoning effort, repair, local windows, and token-length matching. Fix new settings and stopping rules before the new holdout. Do not remove failures, condition-specific rewrite prompts, retry wrong answers, select best-of-N outputs, pool changed protocols, or stop at a favorable CI.
