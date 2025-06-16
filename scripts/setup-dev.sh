#!/bin/bash
# Archivo: scripts/setup-dev.sh
# Script para configurar el entorno de desarrollo

set -e

echo "🚀 Configurando entorno de desarrollo Content Hub..."

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Función para logging
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verificar que Docker está instalado
if ! command -v docker &> /dev/null; then
    log_error "Docker no está instalado. Por favor instala Docker primero."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose no está instalado. Por favor instala Docker Compose primero."
    exit 1
fi

log_info "Docker encontrado: $(docker --version)"
log_info "Docker Compose encontrado: $(docker-compose --version)"

# Crear directorios necesarios
log_info "Creando estructura de directorios..."
mkdir -p docker/postgres/init
mkdir -p docker/mongodb/init
mkdir -p docker/redis
mkdir -p docker/pgadmin
mkdir -p uploads
mkdir -p logs
mkdir -p src/main/resources/db

# Verificar que los archivos de configuración existen
required_files=(
    "docker-compose.yml"
    "docker/postgres/init/01-init-db.sql"
    "docker/mongodb/init/01-init-mongodb.js"
    "docker/redis/redis.conf"
    "docker/pgadmin/servers.json"
    "src/main/resources/db/schema.sql"
)

for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        log_warn "Archivo no encontrado: $file"
        log_warn "Asegúrate de haber creado todos los archivos de configuración."
    fi
done

# Parar contenedores existentes si están corriendo
log_info "Parando contenedores existentes..."
docker-compose down -v || log_warn "No había contenedores corriendo"

# Limpiar volúmenes si se solicita
if [ "$1" = "--clean" ]; then
    log_info "Limpiando volúmenes de datos..."
    docker volume prune -f
    docker-compose down -v --remove-orphans
fi

# Construir e iniciar los servicios
log_info "Iniciando servicios de base de datos..."
docker-compose up -d postgres mongodb redis

# Esperar a que los servicios estén listos
log_info "Esperando a que los servicios estén listos..."
sleep 10

# Verificar el estado de los servicios
services=("postgres" "mongodb" "redis")
for service in "${services[@]}"; do
    if docker-compose ps "$service" | grep -q "Up"; then
        log_info "✅ $service está corriendo"
    else
        log_error "❌ $service no está corriendo"
        docker-compose logs "$service"
        exit 1
    fi
done

# Iniciar herramientas de administración opcionales
if [ "$1" = "--with-admin" ]; then
    log_info "Iniciando herramientas de administración..."
    docker-compose up -d pgadmin mongo-express redis-commander

    log_info "🎉 Herramientas de administración disponibles:"
    echo "  📊 PgAdmin: http://localhost:5050 (admin@contentshub.com / admin123)"
    echo "  🍃 Mongo Express: http://localhost:8081"
    echo "  🔴 Redis Commander: http://localhost:8082"
fi

# Verificar conectividad de bases de datos
log_info "Verificando conectividad de bases de datos..."

# Test PostgreSQL
if docker-compose exec -T postgres psql -U contentshub -d contentshub -c "SELECT 1;" > /dev/null 2>&1; then
    log_info "✅ PostgreSQL conectado correctamente"
else
    log_error "❌ No se pudo conectar a PostgreSQL"
fi

# Test MongoDB
if docker-compose exec -T mongodb mongosh --eval "db.adminCommand('ping')" > /dev/null 2>&1; then
    log_info "✅ MongoDB conectado correctamente"
else
    log_error "❌ No se pudo conectar a MongoDB"
fi

# Test Redis
if docker-compose exec -T redis redis-cli -a contentshub123 ping > /dev/null 2>&1; then
    log_info "✅ Redis conectado correctamente"
else
    log_error "❌ No se pudo conectar a Redis"
fi

# Mostrar información de conexión
log_info "🎉 Entorno de desarrollo configurado exitosamente!"
echo ""
echo "📊 Conexiones de Base de Datos:"
echo "  🐘 PostgreSQL: localhost:5432 (contentshub/contentshub123)"
echo "  🍃 MongoDB: localhost:27017 (contentshub/contentshub123)"
echo "  🔴 Redis: localhost:6379 (password: contentshub123)"
echo ""
echo "🔧 Para gestionar los servicios:"
echo "  Parar:     docker-compose down"
echo "  Reiniciar: docker-compose restart"
echo "  Logs:      docker-compose logs -f [service-name]"
echo "  Estado:    docker-compose ps"
echo ""
echo "🚀 Listo para ejecutar: ./gradlew bootRun"

# Crear archivo de variables de entorno si no existe
if [ ! -f ".env" ]; then
    log_info "Creando archivo .env..."
    cat > .env << EOF
# Variables de entorno para desarrollo
DB_HOST=localhost
DB_PORT=5432
DB_NAME=contentshub
DB_USERNAME=contentshub
DB_PASSWORD=contentshub123

MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_DATABASE=contentshub
MONGO_USERNAME=contentshub
MONGO_PASSWORD=contentshub123

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=contentshub123

JWT_SECRET=mySecretKeyForJWTTokenGenerationMustBeVeryLongAndSecure2024ContentHub
JWT_EXPIRATION=86400000

CORS_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080
WS_ALLOWED_ORIGINS=*
CONTENT_STORAGE_PATH=./uploads
PORT=8080
SPRING_PROFILES_ACTIVE=dev
EOF
    log_info "Archivo .env creado"
fi
