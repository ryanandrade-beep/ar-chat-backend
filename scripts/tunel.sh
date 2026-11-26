#!/usr/bin/env bash
# Abre um túnel HTTPS temporário para o backend e mostra a variável que faz o backend registrar os webhooks sozinho.
# Uso: scripts/tunel.sh [porta]   (padrão 8081). Depois inicie o backend com a variável exibida.
PORTA="${1:-8081}"
LOG="$(mktemp)"
cloudflared tunnel --url "http://localhost:${PORTA}" > "$LOG" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null' EXIT
for _ in $(seq 1 30); do
  URL="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$LOG" | head -1)"
  [ -n "$URL" ] && break
  sleep 1
done
[ -z "$URL" ] && { echo "Não consegui abrir o túnel. Veja $LOG"; exit 1; }
echo "Túnel no ar: $URL"
echo "Inicie o backend com:  ARCHAT_URL_PUBLICA=$URL ./mvnw spring-boot:run"
echo "(Ctrl+C encerra o túnel)"
wait $PID
