#!/usr/bin/env bash
# Usage: ./run.sh <select|run|tables|survivors|selftest|test> [options]
set -euo pipefail
cd "$(dirname "$0")"
[ -d out/classes ] || ./build.sh
if [ "${1:-}" = "test" ]; then
  java -cp out/classes:out/test-classes pipeline.TestRunner
else
  java -Dpipeline.root="$PWD" -cp out/classes pipeline.Main "$@"
fi
