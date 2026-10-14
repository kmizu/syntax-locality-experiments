# P3 design: short-distance dependencies in generation

Status: design approved by the user on 2026-10-14; implementation in progress in the isolated `p3-short-dependency` worktree. Nothing here authorizes HTTP, changes the frozen main plan, or pools P3 with earlier runs. All P3 results are exploratory until a separately frozen testing plan exists.

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

## Invariants carried over

Paired conditions share family, AST, names, values, examples, and lexical mapping. Gold stays separate from live requests. Both oracles stay independent. Malformed closers, indentation, and imports are rejected, not repaired. Live runs require `--execute`, numeric caps, a fresh protocol hash, a frozen plan, and matching capability preflight. Failures are never removed or retried as wrong answers. Structural families remain the sampling clusters.

## Open items

- Token budget per P3a/P3b pilot: count offline with the planner before any live request; no estimate is recorded yet.
- The existing `RenderSpec.indentation` cosmetic flag stays false for all non-`indent` styles.
- Nonce lexicons need an `import` keyword entry for each of the eight fixed sets; add them without reusing existing nonce words.
