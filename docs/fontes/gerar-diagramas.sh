#!/usr/bin/env bash
# Gera as imagens dos diagramas a partir das fontes PlantUML.
# Uso: ./gerar-diagramas.sh /caminho/plantuml.jar   (requer Java; Graphviz recomendado)
set -euo pipefail
JAR="${1:?informe o caminho do plantuml.jar}"
AQUI="$(cd "$(dirname "$0")" && pwd)"
DOCS="$(dirname "$AQUI")"
cd "$AQUI/diagramas"
# PNG (visualização e reserva) e SVG (vetorial, usado no documento para manter a nitidez)
for FMT in png svg; do
  java -Djava.awt.headless=true -DPLANTUML_LIMIT_SIZE=16384 -jar "$JAR" -t$FMT -Sdpi=200 -o "$DOCS/03-diagramas" 0[1-8]*-*.puml
  java -Djava.awt.headless=true -DPLANTUML_LIMIT_SIZE=16384 -jar "$JAR" -t$FMT -Sdpi=200 -o "$DOCS/04-prototipos" 09-*.puml 10-*.puml 11-*.puml
done
echo "Diagramas gerados em 03-diagramas e 04-prototipos"
