#!/bin/bash
# Archivo: scripts/reset-dev.sh
# Script para resetear completamente el entorno de desarrollo

set -e

echo "🔄 Reseteando entorno de desarrollo Content Hub..."

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Confirmación de seguridad
read -p "⚠️  Esto eliminará TODOS los datos de desarrollo. ¿Continuar? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_info "Operación cancelada"
    exit 0
fi

log_warn "Parando todos los contenedores..."
docker-compose down -v --remove-orphans || true

log_warn "Eliminando volúmenes de datos..."
docker volume ls -q --filter name=contentshub | xargs -r docker volume rm || true

log_warn "Eliminando contenedores relacionados..."
docker container ls -a -q --filter name=contentshub | xargs -r docker container rm || true

log_warn "Eliminando imágenes locales..."
docker image ls -q --filter reference=contentshub/* | xargs -r docker image rm || true

log_warn "Limpiando directorios locales..."
rm -rf uploads/*
rm -rf logs/*
mkdir -p uploads logs

log_info "✅ Entorno limpiado completamente"

# Preguntar si quiere reconstruir
read -p "🚀 ¿Quieres reconstruir el entorno ahora? (Y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
    log_info "Reconstruyendo entorno..."
    ./scripts/setup-dev.sh --clean
else
    log_info "Para reconstruir ejecuta: ./scripts/setup-dev.sh"
fi
