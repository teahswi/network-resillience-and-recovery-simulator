#!/usr/bin/env sh
# Builds NetRescue with nothing but the JDK (javac + jar), runs the tests,
# and produces netrescue.jar.   Usage:  ./build.sh        (or: sh build.sh)
set -e
cd "$(dirname "$0")"

rm -rf build
mkdir -p build/classes build/test-classes

echo "1/4 Compiling sources..."
javac -encoding UTF-8 -d build/classes $(find src/main/java -name '*.java')

echo "2/4 Copying web page and sample networks..."
cp -r src/main/resources/. build/classes/

echo "3/4 Compiling and running tests..."
javac -encoding UTF-8 -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java -cp build/classes:build/test-classes netrescue.Tests

echo "4/4 Packaging netrescue.jar..."
jar --create --file netrescue.jar --main-class netrescue.server.App -C build/classes .

echo "Done. Start it with:  java -jar netrescue.jar"
