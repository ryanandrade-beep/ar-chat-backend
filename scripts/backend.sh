#!/usr/bin/env bash
# Sobe o backend pelo terminal com as variáveis do .env da raiz (provedor Z-API, tokens e ARCHAT_URL_PUBLICA).
# Pare antes o backend do IntelliJ, senão a porta 8081 fica ocupada.
set -euo pipefail
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
set -a
# shellcheck disable=SC1091
source "$RAIZ/.env"
set +a
cd "$RAIZ/backend"
PORT="${PORT:-8081}" exec ./mvnw spring-boot:run
