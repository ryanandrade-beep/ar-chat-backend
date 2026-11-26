#!/usr/bin/env bash
# Sobe só o frontend (Vite) na porta 5173.
set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)/frontend"
[ -d node_modules ] || npm install
exec npm run dev -- --port 5173 --strictPort
