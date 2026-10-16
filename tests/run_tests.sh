#!/bin/bash
# JVM unit tests for pure helpers (no Android needed, no external deps).
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT="$PROJECT_DIR/build/tests"
rm -rf "$OUT"
mkdir -p "$OUT"

echo "=== Compiling pure helpers + tests ==="
javac -d "$OUT" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/overlay/EvieProtocol.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/WidgetListCodec.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/WidgetHeightPolicy.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/WidgetSizeTable.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/WidgetReloadPolicy.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/WidgetInflationPolicy.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/ReloadGate.java" \
  "$PROJECT_DIR/app/src/main/java/com/discoveryscreen/widget/StagedQueue.java" \
  "$SCRIPT_DIR/src/"*.java

echo "=== Running tests ==="
java -cp "$OUT" RunTests
