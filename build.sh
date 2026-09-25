#!/usr/bin/env bash
# build.sh — compile lib + tools, run the golden suite. Zero-dependency build:
# javac is all you need. `mvn test` works too if you have Maven.
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p out
javac -d out --release 17 src/main/java/io/github/abhijitkrm/matcher/*.java tests/Golden.java tests/SnapshotTest.java tests/MatcherSnap.java tests/MatcherRun.java tests/MatcherRecover.java bench/MatcherBench.java
java -cp out Golden vectors
java -cp out SnapshotTest vectors
