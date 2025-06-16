# 🚀 RealTime Content Hub

Sistema de gestión de contenido colaborativo en tiempo real construido con Spring Boot 3.5.0, WebFlux y arquitectura reactiva.

## 📋 Características Principales

- **Arquitectura Reactiva** con Spring WebFlux
- **Base de datos híbrida**: PostgreSQL (R2DBC) + MongoDB + Redis
- **Colaboración en tiempo real** con WebSockets
- **Autenticación JWT** y Spring Security Reactive
- **Arquitectura Hexagonal** y Domain-Driven Design
- **Circuit Breaker** con Resilience4j
- **Monitoreo** con Actuator, Prometheus y Grafana
- **Testing completo** con TestContainers

## 🛠️ Stack Tecnológico

- **Java 17** con Spring Boot 3.5.0
- **Spring WebFlux** (Programación Reactiva)
- **PostgreSQL** con R2DBC (datos relacionales)
- **MongoDB** (contenido flexible)
- **Redis** (cache y pub/sub)
- **Docker** & TestContainers
- **Gradle** (Groovy DSL)

## 🚦 Requisitos Previos

- Java 17 o superior
- Docker y Docker Compose
- Gradle 8.x (opcional, usa el wrapper)
- Git

## 🏃‍♂️ Inicio Rápido

### 1. Clonar el repositorio
```bash
git clone https://github.com/tu-usuario/realtime-content-hub.git
cd realtime-content-hub
```

### 2. Levantar los servicios con Docker Compose
```bash
docker-compose up -d
```

Esto levantará:
- PostgreSQL en puerto 5432
- MongoDB en puerto 27017
- Redis en puerto 6379
- Adminer en puerto 8081
- Mongo Express en puerto 8082
- Redis Commander en puerto 8083
- Prometheus en puerto 9090
- Grafana en puerto 3001

### 3. Ejecutar la aplicación

**Opción 1: Usando Gradle Wrapper**
```bash
./gradlew bootRun
```

**Opción 2: Usando tu IDE**
- Importa el proyecto como proyecto Gradle
- Ejecuta la clase `ContentHubApplication`

### 4. Verificar que todo funciona
- API Health: http://localhost:8080/management/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/api-docs

## 📁 Estructura del Proyecto

```
realtime-content-hub/
├── src/main/java/com/contentshub/
│   ├── application/          # Casos de uso y servicios
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── mapper/          # MapStruct mappers
│   │   ├── service/         # Servicios de aplicación
│   │   └── usecase/         # Casos de uso
│   ├── domain/              # Entidades y lógica de negocio
│   │   ├── model/           # Entidades del dominio
│   │   ├── repository/      # Interfaces de repositorio
│   │   ├── event/           # Eventos del dominio
│   │   └── exception/       # Excepciones del dominio
│   ├── infrastructure/      # Implementaciones técnicas
│   │   ├── config/          # Configuraciones
│   │   ├── persistence/     # Implementaciones de repositorios
│   │   │   ├── r2dbc/      # PostgreSQL repositories
│   │   │   ├── mongodb/    # MongoDB repositories
│   │   │   └── redis/      # Redis repositories
│   │   ├── security/        # Seguridad (JWT, filters)
│   │   ├── websocket/       # WebSocket handlers
│   │   ├── cache/          # Configuración de cache
│   │   └── event/          # Event publishers
│   └── presentation/        # Controladores REST
│       ├── controller/      # REST controllers
│       ├── websocket/       # WebSocket endpoints
│       └── handler/         # Exception handlers
```

## 🔐 Seguridad

La aplicación implementa:
- Autenticación basada en JWT
- Spring Security Reactive
- CORS configurado
- Rate limiting con Resilience4j
- Validación de entrada

## 🧪 Testing

**Ejecutar todos los tests:**
```bash
./gradlew test
```

**Ejecutar tests de integración:**
```bash
./gradlew integrationTest
```

Los tests usan TestContainers para levantar contenedores reales de:
- PostgreSQL
- MongoDB
- Redis

## 📊 Monitoreo

- **Actuator Endpoints**: http://localhost:8080/management/
- **Prometheus Metrics**: http://localhost:9090
- **Grafana Dashboards**: http://localhost:3001 (admin/admin123)

## 🐳 Docker

**Construir imagen Docker:**
```bash
./gradlew jibDockerBuild
```

**Ejecutar con Docker:**
```bash
docker run -p 8080:8080 contentshub/backend:latest
```

## 🛠️ Herramientas de Administración

- **Adminer** (PostgreSQL): http://localhost:8081
  - Sistema: PostgreSQL
  - Servidor: postgres
  - Usuario: contentshub_dev
  - Contraseña: dev123
  - Base de datos: contentshub_dev

- **Mongo Express**: http://localhost:8082
  - Usuario: admin
  - Contraseña: admin123

- **Redis Commander**: http://localhost:8083

## 📝 Variables de Entorno

Las principales variables de entorno son:

```bash
# Base de datos
DB_USERNAME=contentshub
DB_PASSWORD=contentshub123
MONGO_USERNAME=contentshub
MONGO_PASSWORD=contentshub123
REDIS_PASSWORD=

# Seguridad
JWT_SECRET=mySecretKey...

# CORS
CORS_ORIGINS=http://localhost:3000

# Puerto
PORT=8080
```

## 🚀 Próximos Pasos

1. Implementar las entidades del dominio
2. Crear los repositorios reactivos
3. Implementar los casos de uso
4. Configurar WebSockets para tiempo real
5. Implementar el sistema de cache
6. Crear los controladores REST
7. Añadir tests unitarios y de integración
8. Configurar CI/CD

## 📄 Licencia

Este proyecto está bajo la licencia Apache 2.0.

## 👥 Contribuir

1. Fork el proyecto
2. Crea tu feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la branch (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

---

¿Necesitas ayuda? Abre un issue en GitHub o contacta al equipo de desarrollo.
