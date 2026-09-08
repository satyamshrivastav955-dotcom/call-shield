#!/bin/sh
# Build verification script for antAI (Phase 0 + Phase 1 edits).
cd "$(dirname "$0")"
export JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.12.8-hotspot"
./gradlew :app:compileDebugKotlin :app:compileDebugKotlinUnitTest :app:compileDebugAndroidTestKotlin --console=plain
