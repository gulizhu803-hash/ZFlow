#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Build the local ZFlow sources. Publishing is a separate, explicit operation.
for service in csdn wecom amap email bocha; do
  docker build --tag "zflow-mcp-server-${service}:local" "$SCRIPT_DIR/mcp-server-$service"
done
