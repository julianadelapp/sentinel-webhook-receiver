#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 21 -d out $(find src/main -name '*.java')
echo "built -> out/"
