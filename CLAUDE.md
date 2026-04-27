# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MALL is a complete e-commerce platform with:
- **Backend**: Spring Boot 2.7.5 microservices (Java 17)
- **Admin Frontend**: Vue.js 2.x with Element UI
- **Mobile Frontend**: Uni-app
- **Database**: MySQL 8.0 with MyBatis ORM
- **Cache**: Redis 7
- **File Storage**: MinIO (S3-compatible)

Architecture: Modular monolith with separate modules for admin API (`mall-admin`), portal/customer API (`mall-portal`), search (`mall-search`), and shared components (`mall-common`, `mall-security`, `mall-mbg`).

## Development Setup

### Prerequisites
- **Option A (Recommended)**: Docker, Java 17+, Maven 3.6+, Node.js 18+
- **Option B (Full Container)**: Only Docker needed

### Quick Start

```bash
# Terminal 1: Start Docker services (MySQL, Redis, MinIO)
docker-compose up -d

# Terminal 2: Start backend (from mall-master/)
mvn clean package -DskipTests
mvn spring-boot:run -pl mall-admin

# Terminal 3: Start frontend (from mall-admin-web-master/mall-admin-web-master)
npm install
npm run dev
```

**Service URLs:**
- Admin UI: http://localhost:8888 (admin/123456)
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
- MySQL: localhost:3306
- Redis: localhost:6379

## Common Build & Development Commands

### Backend (mall-master/)

```bash
# Build everything
mvn clean package -DskipTests

# Build specific module
mvn clean package -DskipTests -pl mall-admin

# Run dev server with hot reload
mvn spring-boot:run -pl mall-admin

# Run tests for specific module
mvn test -pl mall-portal

# Skip tests on full build (default in pom.xml)
mvn clean package
```

**Active Profiles:**
- **dev** (default): `application-dev.yml` — localhost connections
- **docker**: `application-docker.yml` — container service names (mysql, redis, minio)
- **prod**: `application-prod.yml` — production overrides

To use specific profile:
```bash
mvn spring-boot:run -pl mall-admin -Dspring-boot.run.arguments="--spring.profiles.active=docker"
```

### Frontend (mall-admin-web-master/mall-admin-web-master)

```bash
# Development server with hot reload
npm run dev

# Production build
npm build

# Clean dependencies
npm cache clean --force
```

## Backend Architecture

### Module Structure

- **mall-common**: Shared utilities, constants, response wrappers, exceptions
- **mall-mbg**: MyBatis-generated DAO classes and mapper XMLs (typically not edited)
- **mall-security**: JWT authentication and Spring Security filters
- **mall-admin**: Admin backend API (ports 8080, endpoints `/admin/**`)
- **mall-portal**: Customer-facing API (endpoints `/portal/**`)
- **mall-search**: Search functionality via Elasticsearch (optional)
- **mall-demo**: Example/demo code (safe to ignore)

### Key Configuration Points

**Application YAML** (`mall-admin/src/main/resources/application.yml`):
- JWT secret, expiration (7 days default)
- MySQL MyBatis mapper locations: `classpath:dao/*.xml` and `classpath*:com/**/mapper/*.xml`
- Swagger routes whitelisting
- Redis TTL settings (24h default)
- File upload size limit (10MB)

**Data Source Routing**:
- Dev: `jdbc:mysql://localhost:3306/mall` (username/password: root/root)
- Docker: `jdbc:mysql://mysql:3306/mall` (service name via compose network)

### Enhanced Components
The git status shows extensive modifications to portal components (caching, rate limiting, circuit breakers, Kafka integration, alerting). These are in `mall-master/mall-portal/src/main/java/com/macro/mall/portal/component/`. Key ones:
- `RateLimitAspect`, `DatabaseRateLimiter`: Rate limiting enforcement
- `CircuitBreaker`: Fault tolerance pattern
- `LocalCache`, `HotDataPreheater`: Performance optimization
- `KafkaPayMessageSender`, `KafkaPayConsumer`: Async messaging
- `AlertManager`, `DingTalkAlertNotifier`, `EmailAlertNotifier`: Notifications
- `MetricsCollector`: Observability

## Frontend Architecture

### Structure
- `src/main.js`: App entry, API base URL configuration
- `src/router/`: Vue Router routes
- `src/store/`: Vuex state management (typically auth, user state)
- `src/components/`: Reusable Vue components
- `src/views/`: Page-level components (product management, orders, etc.)
- `build/`: Webpack configuration files

### API Integration
Frontend requests are proxied via webpack dev server to backend. Base URL configured in `src/main.js`:
```javascript
const apiBaseUrl = process.env.API_URL || 'http://localhost:8080'
```

## Testing

**Backend tests** exist in `src/test/java` directories of `mall-portal`, `mall-demo`, `mall-search` modules. Tests are skipped by default during builds (`<skipTests>true</skipTests>` in pom.xml).

To run tests:
```bash
mvn test -pl mall-portal
mvn test -pl mall-search
```

**Frontend**: No test configuration found. Use `npm run dev` to manually verify changes in browser.

## Database & Migrations

- **Init script**: `mall.sql` (auto-run on Docker MySQL startup via compose volume)
- **Schema**: MyBatis generates DAOs from schema; edit schema/mapper XMLs directly
- **Connection**: `mysql -h localhost -u root -p mall` (password: `root`)

## Docker Deployment

### Two-Stage Build (Dockerfile)
1. **Builder stage** (maven:3.9): Compiles all modules, outputs `mall-admin.jar`
2. **Runtime stage** (eclipse-temurin:17-jre-alpine): Lean image (~200MB) runs JAR

### Docker Compose Services
- `mysql:8.0`: Main database with `mall.sql` auto-initialization
- `redis:7-alpine`: Cache with AOF persistence
- `minio`: S3-compatible object storage
- `mall-admin` (optional, only with `docker-compose build`): Containerized backend

## Important Notes

- **skipTests default**: Tests are skipped during builds to speed up development. Explicitly run `mvn test` when needed.
- **Default profile**: `dev` uses localhost connections; switch to `docker` profile for container environments.
- **Hot reload**: Backend requires restart (Ctrl+C → re-run); frontend auto-reloads via webpack dev server.
- **Git branch**: `master` uses Spring Boot 2.7+JDK 17; `dev-v3` branch uses Spring Boot 3.2+JDK 17.
- **Port conflicts**: Check `lsof -i :8080` (backend), `:8888` (frontend), `:3306` (MySQL), `:6379` (Redis) if services fail to start.
