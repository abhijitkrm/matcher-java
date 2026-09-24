# Contributing

The contract that keeps this library honest is `spec/` + `vectors/` (vendored
from the [matcher spec repo](https://github.com/abhijitkrm/matcher)):

- **Semantics changes** start upstream in `spec/SPEC.md` plus a golden vector
  (`vectors/**.cmd.jsonl`) with its canonical `.evt.jsonl`. The implementation
  must emit that stream byte-identically — in every index mode the vector
  declares.
- **Verify**: `./build.sh` compiles and replays all golden vectors.
- **Style**: Java 17+, zero dependencies, no frameworks. Records/sealed types
  for the command/event model; flat arrays for the hot path.
- **Performance**: pooled orders, intrusive levels, direct-indexed ladder,
  no per-event allocation pressure beyond the record itself. Benchmarks use
  `tools/vectorgen` workloads per `spec/BENCH.md` — report CPU/OS/JDK, no
  unattributed numbers.
