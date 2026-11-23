#!/usr/bin/env bash
# Dependency-free check of the rule engines (needs only a JDK 21, no Maven, no database).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
javac -d "$OUT" shared/src/main/java/com/ecycle/shared/Enums.java backend/src/main/java/com/ecycle/backend/engine/*.java scripts/engine-smoke/Check.java scripts/engine-smoke/CheckFeatures.java
java -cp "$OUT" Check
java -cp "$OUT" CheckFeatures
