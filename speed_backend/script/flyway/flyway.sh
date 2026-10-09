#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
backend_dir="$(cd "$script_dir/../.." && pwd)"
server_jar="${SPEEDNET_SERVER_JAR:-$backend_dir/speednet-server/target/speednet-server.jar}"
if [[ ! -f "$server_jar" ]]; then
  echo '请先打包 speednet-server，或通过 SPEEDNET_SERVER_JAR 指定已打包的后端 JAR。' >&2
  exit 1
fi
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
unzip -q "$server_jar" 'BOOT-INF/lib/*' -d "$temp_dir"
java_command="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$java_command" --class-path "$temp_dir/BOOT-INF/lib/*" \
  "$script_dir/FlywayMaintenance.java" "$backend_dir" "${1:-check}" "${2:-}"
