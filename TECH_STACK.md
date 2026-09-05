# Tech stack

## Backend

- **Java 25** (via Gradle toolchain), **Spring Boot 4.1.1**
- **Spring MVC** (`spring-boot-starter-webmvc`) and **Spring WebSocket** for the real-time chat transport
- **Spring Data JPA** / **Hibernate** (`spring-boot-starter-data-jpa`)
- **Spring Validation** (`spring-boot-starter-validation`)
- **H2** — file-mode embedded database (`com.h2database:h2`), plus its web console (`spring-boot-h2console`)
- **Lombok**
- **Gradle** (build tool, via wrapper)
- **JUnit 5** + **Mockito** for tests

## Frontend

- **React 19** + `react-dom`
- **Vite 8** (dev server/bundler)
- **oxlint** (linting)
- Plain JavaScript — no TypeScript at runtime (`@types/*` packages are dev-only, for editor support)

## Infra / deployment

- **Docker** multi-stage builds:
  - Backend: `eclipse-temurin:25-jdk` (build stage) → `eclipse-temurin:25-jre` (runtime)
  - Frontend: `node:22-alpine` (build stage) → `nginx:1.27-alpine` (serves the static build and reverse-proxies `/api` and `/ws`)
- **Docker Compose** orchestrating both services, with a named volume (`chat-data`) persisting the H2 database file across container recreation
- **nginx** as reverse proxy / static file server in front of the SPA

## Transport / protocol

- **WebSocket** — native browser `WebSocket` API client-side, Spring WebSocket server-side — for real-time chat
- Plain **REST** (`/api/...`) for login, message history, and catch-up fetch after reconnect

## Notably absent

- No TypeScript
- No external message broker (Kafka etc. is named only as a hypothetical production-scale fix in the README's trade-offs section, not something actually used)
- No external auth/SaaS provider
- No separate frontend test framework
