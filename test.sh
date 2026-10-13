#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
mkdir -p out-test
javac --release 21 -cp out -d out-test $(find src/test -name '*.java')
java -ea -cp out:out-test dev.sentinel.receiver.ReceiverTests
