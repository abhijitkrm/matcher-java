# matcher-java

[![ci](https://github.com/abhijitkrm/matcher-java/actions/workflows/ci.yml/badge.svg)](https://github.com/abhijitkrm/matcher-java/actions/workflows/ci.yml)
[![license](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](LICENSE-MIT)

Deterministic FIFO limit order book and matching engine core for Java —
measured at up to **~11M orders/sec** (see `spec/BENCH.md`).

Single-writer book per symbol, commands in, monotonically sequenced events out.
All I/O hangs off the `Sink` seam; there is no networking, persistence, or
clock dependence in the core. Zero dependencies — plain `javac` is enough.

```java
import io.github.abhijitkrm.matcher.*;

var book = new OrderBook(OrderBook.Config.defaults());
var sink = new Sink.Vec();

book.apply(Command.newLimit(1, Types.Side.Ask, 100, 10, Types.Tif.Gtc), sink);
book.apply(Command.newLimit(2, Types.Side.Bid, 100, 4, Types.Tif.Gtc), sink);
// order 2 filled 4 @100 against order 1 and closed; order 1 keeps 6 resting.
```

## Use it

```xml
<dependency>
  <groupId>io.github.abhijitkrm</groupId>
  <artifactId>matcher</artifactId>
  <version>0.1.0</version>
</dependency>
```

Or vendor `src/main/java` — there are no dependencies, Java 17+.

## Features

- Limit + Market orders, New / Cancel / Replace
- GTC, IOC, FOK, Post-Only
- FIFO price-time priority, maker-price execution, partial fills, sweeps
- Pooled orders in flat arrays, intrusive FIFO price levels, bitmap ladder
  index with `TreeMap` fallback for unbounded prices
- Thin multi-symbol `Engine` router
- Deterministic event streams — verified byte-identically against the shared
  golden vector corpus (`vectors/`)

## Layout

```
src/main/java/io/github/abhijitkrm/matcher/   library
tests/Golden.java     golden vector runner (plain main class)
bench/MatcherBench.java  benchmark harness
build.sh              javac build + golden suite, no Maven needed
vectors/              shared golden corpus (spec repo: github.com/abhijitkrm/matcher)
spec/                 semantics contract (SPEC.md, SCHEMA.md, BENCH.md)
tools/                vectorgen — deterministic workload generator
```

## Test & bench

```bash
./build.sh         # javac + 41 golden vectors — no Maven required

# benchmark (see spec/BENCH.md)
mkdir -p bench/corpora
cargo run --release --manifest-path tools/vectorgen/Cargo.toml -- \
  --workload w2 --n 200000 --setup-n 100000 --out bench/corpora/w2
java -cp out MatcherBench bench/corpora/w2
```

## License

MIT OR Apache-2.0
