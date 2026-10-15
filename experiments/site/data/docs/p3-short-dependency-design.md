# P3 design: short-distance dependencies in generation

Status: design approved by the user on 2026-10-14. P3a and P3b were executed live on 2026-10-14 as exploratory one-replicate pilots (see `docs/progress.md`); P3c is implemented and unexecuted. Nothing here authorizes HTTP, changes the frozen main plan, or pools P3 with earlier runs. All P3 results are exploratory until a separately frozen testing plan exists.

## Hypothesis

A grammar whose dependencies are short-distance (each line can be written and checked from nearby lines) is easier for a model to generate than one whose dependencies span the whole open block structure.

Existing pilot data already separates two senses of "distance" (`runs/verified-pilot-p1`, T3 strict correct per style/depth/filler cell, 8 generations per cell):

| depth | filler 0 / 8 / 32 | A | B | C | D | E |
| ---: | --- | --- | --- | --- | --- | --- |
| 4 | | 8/8/8 | 6/8/8 | 8/8/8 | 8/8/8 | 8/8/8 |
| 8 | | 3/3/4 | 0/0/0 | 3/5/4 | 8/8/8 | 0/0/1 |

Linear distance between opener and closer (filler) shows no effect up to depth 8; the number of simultaneously open blocks (depth) breaks B/E/A. In the partial P2 run, D at depth 16 falls with filler (7/7, 5/5, 2/3; small n). P3 therefore manipulates both axes separately and adds grammars whose closing dependency is genuinely local.

## P3a: closing-construct locality

Styles (same AST, names, values, semantics, examples, and lexical mapping; only syntax differs):

| id | closer | dependency the writer must satisfy |
| --- | --- | --- |
| `generic_end` (B) | `end` | count of open blocks; no local cue |
| `named_end` (D) | `end KIND NAME` | copy of the distant opener's kind and name |
| `depth_end` (G) | `end K` with K = nesting depth of the closed block | previous line's depth only; unfamiliar to pretraining |
| `indent` (F) | none; two-space indentation per level | previous line's indentation only; Python-like (pretraining confound, exploratory) |

Predictions that separate mechanisms: locality predicts G ≥ D > B and F ≥ D; an "explicit identity checklist" account predicts D ≥ G.

Grid: T3 `ast_to_source` only, depths `[4, 8, 16]`, generation fillers `[0, 32, 128]`, natural + nonce, one replicate, eight fixed examples, 36 families (4 per cell) for the pilot. Reading tasks are not planned for P3a.

Parser error codes added: `close_depth_mismatch` (G), `invalid_indent`, `unexpected_indent`, `invalid_dedent` (F). In F a complete program never reports `unclosed_scope`; end of input closes all blocks.

## P3b: import qualification

Language extension shared by every style (AST and semantics identical across qualification conditions):

- A module is an existing block (`unit`/`func`/`area` with a unique name). Modules nest.
- `let x_a 1234` binds a variable in the current module.
- New statement `Import(target)`: brings the bindings made directly inside module `target` into the current scope. `target` must be a module that has already been closed (no forward references, no importing an open ancestor).
- Resolution order for a variable: the current scope's own `let` bindings, then its imports (the later import wins), then the enclosing scopes outward with the same rule. Both the recursive AST oracle and the explicit event-stack oracle implement this independently.
- Qualification styles of the import target:
  - `fq`: `import n_root.n_mid.n_leaf` (full path of module names from the top level). Self-contained for a reader; the writer must reproduce the ancestor chain.
  - `short`: `import n_leaf` (module name only; names are unique). The reader must locate the definition.

Tasks: T1 `scope_lookup` where the probed value is determined by an import (after-close and before-close views retained), and T3 `ast_to_source` with `IMPORT` rows in the neutral table (`tag=IMPORT`, `name=target module`). Diagnostics: `import_path_mismatch` (fq path with a wrong ancestor chain), `import_unknown_module`, `import_open_module`, `invalid_import`.

Grid: closer style fixed to `named_end`, qualification `fq` vs `short`, same depth/filler/regime/family grid as P3a. A full cross with B/D/G/F is not planned; if later wanted it needs its own plan.

## P3c: combined cues

P3a confounds two things in the `indent` style F: indentation carries the structure, and there is no closing line at all. P3c separates them with a 2×2 over (indentation meaningful or not) × (closer none / generic / named):

| | closer none | closer `end` (generic) | closer `end KIND NAME` (named) |
| --- | --- | --- | --- |
| indentation has no meaning | — (no such grammar; a block needs some closer) | `generic_end` (B, from P3a) | `named_end` (D) |
| indentation significant, two spaces per level | `indent` (F) | `indent_generic_end` (I) | `indent_named_end` (H) |

In H and I both cues are checked by the parser and must agree: every body line is indented exactly two spaces deeper than its opener, the closing line sits at the opener's indentation, and a block closes only through its closing line. A dedent without a closing line is `missing_close`; a closing line at any other indentation is `close_indent_mismatch`; wrong labels reuse `close_mismatch` / `close_kind_mismatch` / `close_name_mismatch` (H) or `close_mismatch` (I); indentation faults reuse `invalid_indent`, `unexpected_indent`, `invalid_dedent`; an open block at end of input is `unclosed_scope` as in every closer style. The prompt states both rules and that they must agree. AST, names, values, semantics, examples, and lexical mapping are shared with every other style.

`configs/p3c-pilot.json` runs D, F, H, I on P3a's grid (T3, depths [4, 8, 16], generation fillers [0, 32, 128], natural + nonce, one replicate, eight fixed examples) with P3a's master seed 2026101401, so its 36 structural families are the P3a families by seed. That allows a descriptive family-level comparison of H/I against P3a's B/D/G/F cells; it does not pool the two runs, and B and G are not re-run. Predictions: if the P3a F advantage comes from the local indentation cue, I ≥ B and H ≈ D or better; if it comes from having no closing line to get wrong, H and I fall back toward D and B.

## Invariants carried over

Paired conditions share family, AST, names, values, examples, and lexical mapping. Gold stays separate from live requests. Both oracles stay independent. Malformed closers, indentation, and imports are rejected, not repaired. Live runs require `--execute`, numeric caps, a fresh protocol hash, a frozen plan, and matching capability preflight. Failures are never removed or retried as wrong answers. Structural families remain the sampling clusters.

## Open items

- Token budget per P3a/P3b pilot: count offline with the planner before any live request; no estimate is recorded yet.
- The existing `RenderSpec.indentation` cosmetic flag stays false for all non-`indent` styles.
- Nonce lexicons need an `import` keyword entry for each of the eight fixed sets; add them without reusing existing nonce words.
