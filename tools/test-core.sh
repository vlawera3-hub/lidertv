#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
javac -encoding UTF-8 -d build/core-tests app/src/main/java/tr/lidertv/app/Text.java app/src/main/java/tr/lidertv/app/M3uParser.java core-tests/CoreTest.java
java -cp build/core-tests tr.lidertv.app.CoreTest
