#!/usr/bin/env bash
set -euo pipefail

port="${SERVER_PORT:-${PORT:-8080}}"
exec 3<>"/dev/tcp/127.0.0.1/${port}"
printf 'GET /health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r status <&3
[[ "$status" == "HTTP/1.1 200 "* || "$status" == "HTTP/1.0 200 "* ]]
