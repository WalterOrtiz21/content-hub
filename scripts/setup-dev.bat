@echo off
REM Archivo: scripts/setup-dev.bat
REM Script Batch para configurar el entorno de desarrollo en Windows

echo 🚀 Configurando entorno de desarrollo Content Hub...

REM Verificar Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker no está instalado o no está corriendo
    echo Por favor instala Docker Desktop y asegurate de que este corriendo
    pause
    exit /b 1
)

docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Compose no está instalado
    pause
    exit /b 1
)

echo [INFO] Docker encontrado correctamente

REM Crear directorios
echo [INFO] Creando estructura de directorios...
if not exist "docker\postgres\init" mkdir "docker\postgres\init"
if not exist "docker\mongodb\init" mkdir "docker\mongodb\init"
if not exist "docker\redis" mkdir "docker\redis"
if not exist "docker\pgadmin" mkdir "docker\pgadmin"
if not exist "uploads" mkdir "uploads"
if not exist "logs" mkdir "logs"
if not exist "src\main\resources\db" mkdir "src\main\resources\db"

REM Parar contenedores existentes
echo [INFO] Parando contenedores existentes...
docker-compose down >nul 2>&1

REM Iniciar servicios
echo [INFO] Iniciando servicios de base de datos...
docker-compose up -d postgres mongodb redis

REM Esperar a que estén listos
echo [INFO] Esperando a que los servicios estén listos...
timeout /t 15 /nobreak >nul

REM Verificar estado
echo [INFO] Verificando estado de servicios...
docker-compose ps

REM Preguntar por herramientas de admin
set /p admin="¿Quieres iniciar las herramientas de administración? (y/N): "
if /i "%admin%"=="y" (
    echo [INFO] Iniciando herramientas de administración...
    docker-compose up -d pgadmin mongo-express redis-commander
    echo.
    echo 🎉 Herramientas de administración disponibles:
    echo   📊 PgAdmin: http://localhost:5050 ^(admin@contentshub.com / admin123^)
    echo   🍃 Mongo Express: http://localhost:8081
    echo   🔴 Redis Commander: http://localhost:8082
)

REM Crear archivo .env si no existe
if not exist ".env" (
    echo [INFO] Creando archivo .env...
    (
        echo # Variables de entorno para desarrollo
        echo DB_HOST=localhost
        echo DB_PORT=5432
        echo DB_NAME=contentshub
        echo DB_USERNAME=contentshub
        echo DB_PASSWORD=contentshub123
        echo.
        echo MONGO_HOST=localhost
        echo MONGO_PORT=27017
        echo MONGO_DATABASE=contentshub
        echo MONGO_USERNAME=contentshub
        echo MONGO_PASSWORD=contentshub123
        echo.
        echo REDIS_HOST=localhost
        echo REDIS_PORT=6379
        echo REDIS_PASSWORD=contentshub123
        echo.
        echo JWT_SECRET=mySecretKeyForJWTTokenGenerationMustBeVeryLongAndSecure2024ContentHub
        echo JWT_EXPIRATION=86400000
        echo.
        echo CORS_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080
        echo WS_ALLOWED_ORIGINS=*
        echo CONTENT_STORAGE_PATH=./uploads
        echo PORT=8080
        echo SPRING_PROFILES_ACTIVE=dev
    ) > .env
)

echo.
echo 🎉 Entorno de desarrollo configurado exitosamente!
echo.
echo 📊 Conexiones de Base de Datos:
echo   🐘 PostgreSQL: localhost:5432 ^(contentshub/contentshub123^)
echo   🍃 MongoDB: localhost:27017 ^(contentshub/contentshub123^)
echo   🔴 Redis: localhost:6379 ^(password: contentshub123^)
echo.
echo 🔧 Para gestionar los servicios:
echo   Parar:     docker-compose down
echo   Reiniciar: docker-compose restart
echo   Logs:      docker-compose logs -f [service-name]
echo   Estado:    docker-compose ps
echo.
echo 🚀 Listo para ejecutar: gradlew.bat bootRun
echo.
pause
