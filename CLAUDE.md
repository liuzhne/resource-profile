# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Resource-Profile is a **Teacher-Student Resource Portrait System** (师生资源画像系统) — a microservices-based educational platform with a Vue 3 frontend, Spring Boot backend, and a Python AI inference sidecar (LLM + RAG over Milvus).

## EduCare 子系统路线图

The AI subsystem (agent-service + ai-inference-service + Multi-Agent + RAG + 本地 LLM) is tracked in **`docs/educare/EXECUTION_PLAN.md`** — single source of truth for Phase G/H/I 的可执行原子任务清单、§1 下一步指针、§6 变更记录、§8 已知阻塞。当前状态：Phase G 代码侧基本完成（仅 G-2.3/G-3.4 待用户实跑验证），Phase H 启动中（H-1.1 完成，下一步 H-1.2 student-data MCP server），Phase I 储备。设计源（决策依据）见 `docs/educare/IMPROVEMENT_2026_MAY.md` (v1.1, 2026-05-12 拍板)。**Read EXECUTION_PLAN.md first** instead of grepping git log or prior session jsonls. After finishing an atomic task, follow §0 Update Protocol: 勾选 + 追加 `完成于 YYYY-MM-DD：备注` 行 + 更新 §1 指针 + 顶部"最近更新"。

## Architecture

**Top-level layout:**
- `/backend` — Java/Spring Boot microservices (Maven multi-module)
- `/ai-inference-service` — Python FastAPI service for LLM inference & vector retrieval
- `/frontend` — Vue 3 + Vite single-page application
- `/docker` — Docker and docker-compose configuration
- `/sql` — Database initialization scripts

The Java side handles business logic, persistence, and orchestration. AI calls go through `agent-service`, which either talks to a local LLM via Spring AI's OpenAI-compatible client (default) or delegates to the Python `ai-inference-service` via Feign for RAG / embedding-heavy work.

## Backend — Spring Boot Microservices

**Build Tool:** Maven 3+
**Java Version:** 17
**Spring Boot:** 3.2.5
**Spring Cloud:** 2023.0.1
**Spring Cloud Alibaba:** 2023.0.1.0
**Spring AI:** 1.1.6 (GA, on Maven Central — no milestone repo needed)

**Microservices Modules:**

| Module | Port | Purpose |
|--------|------|---------|
| `gateway` | 8080 | Spring Cloud Gateway, routes to all services |
| `auth-service` | 8081 | Authentication & Authorization |
| `user-service` | 8082 | User management |
| `teacher-service` | 8083 | Teacher profile management |
| `student-service` | 8084 | Student profile management |
| `mental-service` | 8085 | Mental health assessment |
| `data-service` | 8086 | Data analysis and dashboard |
| `agent-service` | 8087 | LLM/Agent orchestration (Spring AI + Feign to Python) |
| `common` | — | Shared library (JWT, Result wrapper) |

**Key Dependencies:**
- MyBatis Plus 3.5.11 (ORM)
- JWT 0.12.5 (jjwt-api, jjwt-impl, jjwt-jackson)
- MySQL 8.0.33
- Spring Cloud Alibaba Nacos (Service Discovery + Configuration)
- Spring AI OpenAI starter (`spring-ai-openai-spring-boot-starter`) — used by `agent-service`
- Spring Cloud OpenFeign — used by `agent-service` to call `ai-inference-service`
- Lombok 1.18.30

**Backend Code Patterns:**

All services follow a consistent layered architecture:
- `entity/` — MyBatis Plus entities using Lombok `@Data`
- `mapper/` — Mappers extending `BaseMapper<Entity>` (no XML mappers; all SQL via annotations or MyBatis Plus wrappers)
- `service/` + `service/impl/` — Service interfaces and `@Service` implementations
- `controller/` — `@RestController` with `@RequestMapping` and `@RequiredArgsConstructor` for dependency injection
- `dto/` — Request/response DTOs using Lombok
- `config/` — Spring `@Configuration` classes
- `exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` handling validation and runtime exceptions
- `feign/` — (agent-service) Feign clients for remote services like `AiInferenceClient`

**MyBatis Plus Global Config** (in each `application.yml`):
- Logic delete: field `deleted`, value `1` = deleted, `0` = not deleted
- ID type: `auto` (database auto-increment)
- `map-underscore-to-camel-case: true`

**API Response Pattern:**
All controllers return `Result<T>` from the `common` module:
- `Result.success(data)` → code 200
- `Result.error(message)` → code 500
- `Result.error(code, message)` → custom code

**Nacos Config Pattern:**
Every service imports two Nacos config files:
```yaml
config:
  import:
    - optional:nacos:{service-name}.yml?group=DEFAULT_GROUP
    - optional:nacos:common.yml?group=DEFAULT_GROUP
```

Nacos is configured with auth (`NACOS_AUTH_IDENTITY_KEY=serverIdentity`, `NACOS_AUTH_IDENTITY_VALUE=security`); each service `application.yml` references these via env vars.

**Gateway Routing:**
Routes use load-balanced URIs (`lb://{service-name}`) with `StripPrefix=0`:
- `/auth/**` → auth-service
- `/user/**` → user-service
- `/teacher/**` → teacher-service
- `/student/**` → student-service
- `/mental/**` → mental-service
- `/data/**` → data-service
- `/agent/**` → agent-service

Global CORS is configured on the gateway (`allowedOrigins: "*"`).

**JWT Authentication Flow:**
- `JwtUtil` (common module) generates and parses tokens using HS256
- Access token expires in 24 hours, refresh token in 7 days
- Secret configured via `jwt.secret` (reads `JWT_SECRET` env var if mapped)
- On login: tokens generated, access token stored in Redis as `token:{userId}` with 24h TTL
- Frontend sends `Authorization: Bearer {token}` header
- Auth endpoints (`/auth/**`) are public; all other requests require authentication, **enforced at the gateway by `JwtAuthGlobalFilter`** (validates JWT signature + expiry, then checks the Redis session whitelist `token:{userId}` so logout/password-change revokes old tokens → 401 on failure; accepts `Authorization: Bearer` or `?token=` for SSE; `**/_internal/**` paths → 403; toggles `educare.gateway.auth.enabled` / `educare.gateway.auth.check-session`, both default on). `auth-service`'s own Spring Security only protects auth-service itself.
- **Horizontal authorization (IDOR)** is enforced per-endpoint via `common`'s `AccessGuard.allowSelfRoleOrInternal` (self or privileged staff role; tokenless internal Feign calls trusted) across student/mental/agent/data controllers.
- **Field-level permission** (`@SensitiveField` + `FieldPermissionAdvice`, `educare.field-permission.enabled`) **defaults on**: filters response fields by role for token-bearing end-user requests; tokenless internal Feign calls pass through unmasked (so the AI portrait chain keeps full data). See `docs/educare/FIELD_PERMISSION.md`.

## agent-service — AI Orchestration

`agent-service` is the bridge between business services and LLM/RAG capabilities.

- **Spring AI ChatClient** (configured in `SpringAiConfig`, single local `@Primary` bean) is wired against an OpenAI-compatible endpoint set by `spring.ai.openai.base-url` (defaults to `http://host.docker.internal:8091` — i.e., a llama.cpp / vLLM server running on the host) with model from `spring.ai.openai.chat.options.model` (default `qwen2.5-14b`). Use the fluent API: `chatClient.prompt().system(...).user(...).call().content()` or `.entity(Class)` for structured output (see `RiskAnalyzeService`).
- **Feign client** `AiInferenceClient` (`name = "ai-inference-service"`, URL `${ai.inference.url:http://host.docker.internal:8090}`) calls into the Python service for `/api/v1/agent/risk`, `/api/v1/rag/retrieve`, `/api/v1/agent/plan`, and `/api/v1/agent/audit`. Use this when the call needs vector search or LangChain-side logic; use `ChatClient` directly for plain LLM calls.
- Async work is dispatched via the executor in `AsyncConfig`.
- MyBatis-Plus auto-fill is wired in `MyMetaObjectHandler`.

## ai-inference-service — Python FastAPI

**Runtime:** Python 3.10, FastAPI 0.110, uvicorn (single worker), default port **8090**.

**Stack:**
- LangChain 0.1.12 + `langchain-openai` for LLM chains against an OpenAI-compatible backend
- `pymilvus` 2.4.1 for vector store access
- `httpx`, `tenacity` for outbound calls and retries

**Layout** (`/ai-inference-service/app`):
- `main.py` — FastAPI factory, CORS, router registration
- `core/config.py` — `Settings` reading env vars (`LLM_BASE_URL`, `LLM_MODEL`, `MILVUS_HOST`, `MILVUS_PORT`, `EMBEDDING_BASE_URL`, etc.)
- `api/health.py`, `api/llm.py` — HTTP routers; add new endpoints here
- `services/llm_client.py` — LLM client wrapper

**Important env defaults:**
- `LLM_BASE_URL` → host LLM server (OpenAI-compatible)
- `MILVUS_HOST` defaults to `milvus-standalone` (the docker-compose service name on `edu-network`)
- The container reaches the host via `host.docker.internal`

## Frontend — Vue 3

**Build Tool:** Vite 5.2.8
**Framework:** Vue 3.4.21
**Package Manager:** npm

**Key Dependencies:**
- `vue-router` 4.3.0 — Client-side routing
- `pinia` 2.1.7 — State management
- `element-plus` 2.6.3 — UI Component Library
- `@element-plus/icons-vue` 2.3.1 — Icons
- `axios` 1.6.8 — HTTP client
- `echarts` 5.5.0 + `vue-echarts` 6.6.9 — Data visualization
- `js-cookie` 3.0.5 — Cookie management
- `nprogress` 0.2.0 — Progress bar

**Vite Config:**
- Dev Server: `http://localhost:5173`
- API Proxy: `/api` → `http://localhost:8080` (path-rewrite strips `/api`)
- Auto-import: `unplugin-auto-import` for Vue/Vue Router/Pinia APIs and Element Plus components
- Alias: `@` → `src`

**Frontend Patterns:**

- **API Layer:** Each backend service has a corresponding module in `src/api/`. All use the same `axios` instance from `@/utils/request` which:
  - Attaches `Authorization: Bearer {token}` from Pinia store automatically
  - Intercepts responses: shows `ElMessage.error()` on non-200 codes
  - Handles 401 by calling `userStore.logout()` and redirecting to login
  - Base URL defaults to `/api` (proxied to gateway in dev)

- **State Management:** Pinia stores use the Composition API pattern (`defineStore` with `ref`/`computed`). Token is persisted to `localStorage`. Key stores: `user` (auth state), `app` (UI state like sidebar collapse).

- **Router Guards:** Before each route: starts NProgress, checks token, fetches user info if missing, redirects unauthenticated users to `/login` unless route has `meta.public: true`. Admin-only routes use `meta.roles: ['admin']`.

- **Layout:** Main layout at `src/components/Layout/index.vue` with sidebar, navbar, breadcrumb, and tags-view.

## Docker & Infrastructure

**Services in `docker-compose.yml`:**

| Service | Image | Ports | Purpose |
|---------|-------|-------|---------|
| mysql | mysql:8.0 | 3306:3306 | Primary database |
| redis | redis:7-alpine | 6379:6379 | Cache & session storage |
| nacos | nacos/nacos-server:v2.3.0 | 8848, 9848 | Service discovery & config |
| gateway | Custom build | 8080:8080 | API gateway |
| ai-inference-service | Custom build | 8090:8090 | Python LLM/RAG service |
| milvus-standalone | milvusdb/milvus:v2.4.1 | 19530, 9091 | Vector database |
| etcd | quay.io/coreos/etcd:v3.5.5 | — | Milvus metadata |
| minio | minio/minio | 9000, 9001 | Milvus object storage (console on 9001) |
| attu | zilliz/attu:v2.4 | 8000:3000 | Milvus Web UI |

**Database:**
- Name: `edu_portrait`
- User: `edu` / `edu123456`
- Root password: `root`
- Initialization: `/sql/init/01_init.sql`
- Default accounts (all use bcrypt hash, password equals username): `admin`, `teacher`, `student`

## Common Commands

**Backend (Maven):**
```bash
# Build all modules
cd backend
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run individual service (from service directory)
mvn spring-boot:run

# Run a single test class / method
mvn -pl <module> test -Dtest=ClassName
mvn -pl <module> test -Dtest=ClassName#methodName
```

Note: Spring AI 1.1.6 is GA on Maven Central — no milestone repo needed (the `repo.spring.io/milestone` declaration was removed when upgrading off 1.0.0-M6).

**Frontend (npm):**
```bash
cd frontend

npm install
npm run dev        # Vite dev server on :5173
npm run build      # Production build to dist/
npm run preview    # Serve production build
npm run lint       # ESLint --fix on .vue/.js/.jsx/.cjs/.mjs
npm run format     # Prettier on src/
```

**ai-inference-service (Python):**
```bash
cd ai-inference-service

# Local dev (Python 3.10)
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload

# Or build & run via docker-compose along with Milvus
cd ../docker && docker-compose up -d ai-inference-service milvus-standalone
```

There is no Python test runner configured yet; if adding tests, prefer `pytest`.

**Docker:**
```bash
cd docker

docker-compose up -d                    # all infra
docker-compose up -d mysql redis nacos  # backend deps only
docker-compose logs -f <service>
docker-compose down                     # stop
docker-compose down -v                  # stop + drop volumes (wipes MySQL/Redis data)
```

## Configuration & Environment Variables

**Nacos Configuration:**
- Server: `localhost:8848` (default)
- Env vars: `NACOS_SERVER`, `NACOS_NAMESPACE`, `NACOS_USERNAME`, `NACOS_PASSWORD`, `NACOS_AUTH_IDENTITY_KEY`, `NACOS_AUTH_IDENTITY_VALUE`

**MySQL Configuration:**
- Env vars: `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`

**Redis Configuration:**
- Env vars: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

**JWT Configuration:**
- `JWT_SECRET`, access token 24h, refresh token 7d

**LLM / AI Configuration:**
- `LLM_BASE_URL` — OpenAI-compatible endpoint URL (host LLM)
- `LLM_MODEL`, `LLM_TEMPERATURE`, `LLM_MAX_TOKENS`, `LLM_API_KEY`
- `MILVUS_HOST`, `MILVUS_PORT`
- `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL`
- `ai.inference.url` (Java) — overrides the Feign target for `AiInferenceClient`

## Key File Locations

| Purpose | Path |
|---------|------|
| Parent POM (Spring AI milestone repo defined here) | `/backend/pom.xml` |
| Gateway Config | `/backend/gateway/src/main/resources/application.yml` |
| Auth Config | `/backend/auth-service/src/main/resources/application.yml` |
| Agent Config | `/backend/agent-service/src/main/resources/application.yml` |
| Spring AI ChatClient wiring | `/backend/agent-service/src/main/java/com/edu/agent/config/SpringAiConfig.java` |
| Feign → Python | `/backend/agent-service/src/main/java/com/edu/agent/feign/AiInferenceClient.java` |
| Python entrypoint | `/ai-inference-service/app/main.py` |
| Python settings | `/ai-inference-service/app/core/config.py` |
| Frontend Config | `/frontend/vite.config.js` |
| Docker Compose | `/docker/docker-compose.yml` |
| Database Init | `/sql/init/01_init.sql` |
| JWT Utility | `/backend/common/src/main/java/com/edu/common/util/JwtUtil.java` |
| Result Wrapper | `/backend/common/src/main/java/com/edu/common/result/Result.java` |
| API Request | `/frontend/src/utils/request.js` |
| Router | `/frontend/src/router/index.js` |
| User Store | `/frontend/src/store/modules/user.js` |
