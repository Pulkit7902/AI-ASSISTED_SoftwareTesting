#!/usr/bin/env bash
# Compile the pipeline (needs a JDK 17+; no Maven, no third-party libraries).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out && mkdir -p out/classes out/test-classes
javac -encoding UTF-8 -Xlint:all -d out/classes src/main/java/pipeline/*.java
javac -encoding UTF-8 -cp out/classes -d out/test-classes src/test/java/pipeline/*.java
echo "built -> out/classes"
