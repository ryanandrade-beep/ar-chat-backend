#!/usr/bin/env bash
# Túnel com endereço FIXO (ngrok), para o webhook da Z-API não mudar a cada execução.
# Uso: NGROK_DOMINIO=seu-nome.ngrok-free.app scripts/tunel-fixo.sh [porta]
# Requer: ngrok config add-authtoken <token> (uma vez) e o domínio gratuito criado no painel do ngrok.
PORTA="${1:-8081}"
[ -z "$NGROK_DOMINIO" ] && { echo "Defina NGROK_DOMINIO (ex.: NGROK_DOMINIO=seu-nome.ngrok-free.app)"; exit 1; }
echo "Backend deve usar: ARCHAT_URL_PUBLICA=https://${NGROK_DOMINIO}"
exec ngrok http --url="${NGROK_DOMINIO}" "${PORTA}"
