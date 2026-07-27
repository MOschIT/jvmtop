#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Building jvmtop..."
mvn clean package

VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
JAR="./target/jvmtop-${VERSION}.jar"
LIB="./target/lib"

if [ -d "$LIB" ] && [ -n "$JAR" ]; then
  echo "Build successful."
  echo "JAR: $JAR"
  echo "Dependencies: $LIB/"
  echo ""
  echo "Run with:"
  echo "  java --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \\"
  echo "       --add-opens=jdk.management.agent/jdk.internal.agent=ALL-UNNAMED \\"
  echo "       --add-opens=java.rmi/sun.rmi.server=ALL-UNNAMED \\"
  echo "       --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED \\"
  echo "       -jar $JAR"
else
  echo "Build failed: JAR or lib directory not found."
  exit 1
fi
