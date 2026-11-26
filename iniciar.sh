#!/usr/bin/env bash
# Sobe o A&R Chat inteiro: Docker (Colima), Postgres, IntelliJ (onde roda o backend), DBeaver, frontend no Terminal e o painel no Chrome.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")" && pwd)"

echo "==> Docker (Colima)"
if ! docker info >/dev/null 2>&1; then
  colima start
fi

echo "==> Banco PostgreSQL (porta 5433)"
if docker ps -a --format '{{.Names}}' | grep -qx archat-postgres; then
  docker start archat-postgres >/dev/null
else
  docker run -d --name archat-postgres --restart unless-stopped \
    -e POSTGRES_DB=archat -e POSTGRES_USER=archat -e POSTGRES_PASSWORD=archat \
    -p 5433:5432 -v archat-dados-postgres:/var/lib/postgresql/data postgres:16-alpine >/dev/null
fi
until docker exec archat-postgres pg_isready -U archat -d archat >/dev/null 2>&1; do sleep 1; done
echo "    banco pronto"

echo "==> IntelliJ e DBeaver"
open -a "IntelliJ IDEA CE" "$RAIZ"
open -a DBeaver

no_terminal() {
  osascript - "$1" <<'EOF'
on run argv
  tell application "Terminal"
    activate
    do script (item 1 of argv)
  end tell
end run
EOF
}

echo "==> Frontend (nova janela do Terminal)"
if curl -s -o /dev/null http://localhost:5173; then
  echo "    já está rodando na porta 5173"
else
  no_terminal "cd '$RAIZ/frontend' && { [ -d node_modules ] || npm install; } && npm run dev -- --port 5173 --strictPort" >/dev/null
fi

echo "==> Backend: no terminal (pode ser o do IntelliJ), rode:"
echo "    cd $RAIZ/backend && PORT=8081 ./mvnw spring-boot:run"
echo "    aguardando a API na porta 8081..."
until curl -s -o /dev/null http://localhost:8081; do sleep 2; done
until curl -s -o /dev/null http://localhost:5173; do sleep 1; done
open -a "Google Chrome" http://localhost:5173

echo
echo "Tudo no ar:"
echo "  Painel web  http://localhost:5173"
echo "  API/Swagger http://localhost:8081   (admin / admin12345)"
echo "  Banco       localhost:5433  base/usuário/senha: archat"
echo "Para parar: Ctrl+C no terminal do backend e na janela do frontend."
