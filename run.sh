#!/bin/bash
cd "$(dirname "$0")"

JAR=$(ls target/jvmtop-*SNAPSHOT.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "JAR not found. Run 'bash build.sh' first."
  exit 1
fi

exec java --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED \
         --add-opens=jdk.management.agent/jdk.internal.agent=ALL-UNNAMED \
         --add-opens=java.rmi/sun.rmi.server=ALL-UNNAMED \
         --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED \
         -jar "$JAR" "$@"
