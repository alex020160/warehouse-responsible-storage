#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
case "$("$JAVA_HOME/bin/javac" -version 2>&1)" in 'javac 21.'*) ;; *) echo 'JDK 21 required' >&2; exit 1;; esac
mkdir -p lib build/classes
jar=lib/gson-2.13.2.jar
test -f "$jar" || curl -fL https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar -o "$jar"
if command -v sha256sum >/dev/null 2>&1; then actual=$(sha256sum "$jar" | cut -d ' ' -f 1); else actual=$(shasum -a 256 "$jar" | cut -d ' ' -f 1); fi
test "$actual" = dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0 || { echo 'Invalid Gson SHA-256' >&2; exit 1; }
find src/main/java src/test/java -name '*.java' | sed 's/.*/"&"/' > build/sources.txt
"$JAVA_HOME/bin/javac" --release 21 -encoding UTF-8 -cp "$jar" -d build/classes @build/sources.txt
if test "${1:-}" = --test; then "$JAVA_HOME/bin/java" -cp 'build/classes:lib/gson-2.13.2.jar' ru.mirea.warehouse.VerificationMain --self-test; fi
