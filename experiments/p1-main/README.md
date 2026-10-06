# Frozen P1 reading-main source

`source/` preserves the production Scala and build files corresponding to source hash `1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1`. This historical implementation is separate from the current P2 source at the repository root.

With JDK 21 and sbt 1.10.7:

```sh
cd experiments/p1-main/source
sbt test
sbt 'bench / run --help'
```

The public main snapshot is a provisional reading result. Regrading or resuming the frozen run requires its original complete run artifacts and saved matching capabilities/freeze, not just the public summary CSV files. Do not create new P2 trials with this historical implementation or resume the old main with the current P2 implementation. No command here authorizes new live spending.
