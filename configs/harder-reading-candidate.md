# Conditional harder-reading candidate

`harder-reading-candidate.json` implements only the candidate already specified in section 7.3a of `scala3-syntax-locality-icl-experiment.md`: depths `[8,16,32]` and reading fillers `[128,512,2048]`.

The fresh master seed is `2026100601`, distinct from the original pilot's `20261005`. It produces a separate holdout using the existing deterministic seed derivation. Every other field is copied unchanged from `pilot.json`, including generation fillers `[0,8,32]`, output caps, examples, replicates, concurrency, and rate limits.

The saved plan is `runs/harder-reading-candidate-p0`. It is an unexecuted, conditional P0/T1 candidate. Preparing or auditing it does not select the difficulty grid or authorize HTTP. It is separate from the ongoing full P1 pilot and is not a replacement P1 generation grid.

Offline evidence and request-length definitions are recorded in `runs/harder-reading-candidate-p0/candidate-audit.md`.
