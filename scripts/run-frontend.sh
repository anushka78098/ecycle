#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -q -pl frontend -am -DskipTests install
mvn -q -f frontend/pom.xml javafx:run
