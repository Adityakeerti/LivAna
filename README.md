# LivAna — Backend

> **Living Analytics** — a platform connecting students, business owners, and city services.  
> This repository contains the backend API built with Spring Boot 4, PostgreSQL 16, and Valkey 7.

---

## Project Status

| Phase | Module | Status |
|-------|--------|--------|
| Phase 1 | Infrastructure & Project Setup | ✅ Complete |
| Phase 2 · Dev 1 | Auth, Security & User Management | ✅ Complete & Verified |
| Phase 2 · Dev 2–6 | Remaining API modules | 🔜 Next |

---

## Architecture Overview

```
Android / iOS App
       │  Google ID Token
       ▼
POST /api/auth/google
       │
       ├─ GoogleTokenValidator  (cryptographic verification)
       ├─ UserService.upsertFromGoogle  (PostgreSQL)
       ├─ JwtService.generateAccessToken  (15-min HMAC-SHA512 JWT)
       └─ RefreshTokenService.create  (7-day opaque token in Valkey)
       │
       ▼
{ accessToken, refreshToken, user }

Every subsequent request:
  Authorization: Bearer <accessToken>
       │
       ├─ RateLimitFilter  (100 req/min per IP via Bucket4j + Valkey)
       └─ JwtAuthFilter    (validates JWT → injects userId into SecurityContext)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 (Spring Framework 7) |
| Security | Spring Security 7 — Stateless / JWT |
| Auth | Google Sign-In (google-api-client) + JJWT 0.12.x |
| Database | PostgreSQL 16 (JPA / Hibernate 7) |
| Cache / Rate Limit | Valkey 7 (Redis-compatible) via Lettuce + Bucket4j |
| Build | Maven 3 (Maven Wrapper included) |
| Containerisation | Docker Compose |

---

## Phase 2 · Dev 1 — Auth & Security (Complete)

### Implemented

- **`JwtService`** — HMAC-SHA512 access tokens (15-min TTL). Secret read from `APP_JWT_SECRET` env var (≥ 64 chars enforced).
- **`GoogleTokenValidator`** — Cryptographic verification of Google ID tokens via Google's public keys. Audience mismatch → 401.
- **`RefreshTokenService`** — Opaque UUID tokens stored in Valkey with 7-day TTL. O(1) revocation on logout.
- **`JwtAuthFilter`** — `OncePerRequestFilter`. Validates Bearer token → injects `userId` (Long) as `AuthenticationPrincipal`.
- **`RateLimitFilter`** — 100 req/min per IP. Returns `429 Too Many Requests` with `Retry-After: 60` header.
- **`SecurityConfig`** — STATELESS session, CSRF disabled, custom JSON 401/403 responses.
- **`CorsConfig`** — Allows `localhost:8080`, `localhost:3000`, `localhost:5173` for dev.
- **`AppConfig`** — Centralized `ObjectMapper` + Bucket4j `ProxyManager` beans (decoupled to prevent circular dependencies).
- **`User` entity & `UserRepository`** — JPA entity with PostgreSQL native enum (`user_role`).
- **`UserService`** — `upsertFromGoogle`: find-or-create on every login, updates name/avatar.
- **`GlobalExceptionHandler`** — Consistent JSON error shape for 400 / 401 / 403 / 409 / 500.

### Auth Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/google` | Public | Exchange Google ID token → JWT pair |
| `POST` | `/api/auth/refresh` | Public | Exchange refresh token → new access token |
| `POST` | `/api/auth/logout` | JWT required | Revoke refresh token |
| `GET` | `/api/users/me` | JWT required | Get current user profile |
| `PATCH` | `/api/users/me` | JWT required | Update name / phone |

### Test Verification (all 7 checkpoints ✅)

| # | Test | Result |
|---|------|--------|
| 1 | `POST /api/auth/google` → accessToken + refreshToken | ✅ HTTP 200 |
| 2 | `GET /api/users/me` with JWT | ✅ HTTP 200 |
| 3 | `POST /api/auth/refresh` → new accessToken | ✅ HTTP 200 |
| 4 | `POST /api/auth/logout` | ✅ HTTP 200 |
| 5 | `POST /api/auth/refresh` with revoked token | ✅ HTTP 401 |
| 6 | `GET /api/users/me` without token | ✅ HTTP 401 |
| 7 | Rate limit stress test (110 req) | ✅ HTTP 429 after req 100 |

---

## Getting Started

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (for PostgreSQL + Valkey)
- Java 21 JDK
- Maven (or use the included `mvnw` wrapper)

### 1. Set Environment Variables

```bash
# Required — minimum 64 characters
export APP_JWT_SECRET="your-super-secret-64-char-string-here-use-openssl-rand"
```

On Windows PowerShell:
```powershell
$env:APP_JWT_SECRET = "your-super-secret-64-char-string-here-use-openssl-rand"
```

### 2. Configure Google OAuth Client ID

Edit `backend/src/main/resources/application-dev.properties`:

```properties
app.google.client-id=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Create a Web Application OAuth 2.0 client in [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials.

### 3. Start Infrastructure (PostgreSQL + Valkey)

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL 16** on port `5432` (DB: `livana`, schema auto-created from `db/01_schema.sql`)
- **Valkey 7** on port `6379`

### 4. Run the Backend

```bash
cd backend
./mvnw spring-boot:run          # Linux / macOS
.\mvnw.cmd spring-boot:run      # Windows
```

Backend starts on **http://localhost:8080**

### 5. Open the Test Console

Navigate to **http://localhost:8080/test.html** in your browser.

> ⚠️ Open via `http://localhost:8080/test.html`, NOT by double-clicking the file — browsers block `fetch()` from `file://` origins.

---

## Project Structure

```
LivAna/
├── backend/                        # Spring Boot application
│   ├── src/main/java/com/livana/backend/
│   │   ├── auth/                   # JWT, Google OAuth, Refresh Token, Auth endpoints
│   │   ├── security/               # SecurityConfig, JwtAuthFilter, RateLimitFilter, CorsConfig, AppConfig
│   │   ├── user/                   # User entity, repository, service, controller
│   │   └── exception/              # GlobalExceptionHandler, custom exceptions
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-dev.properties
│   │   └── static/test.html        # Auth test console (served at /test.html)
│   └── pom.xml
├── db/
│   └── 01_schema.sql               # PostgreSQL schema (auto-run by Docker on first start)
├── docs/                           # Architecture diagrams, UI flows, implementation plans
├── docker-compose.yml
└── test.html                       # Source copy of the test console
```

---

## Security Notes

- **`APP_JWT_SECRET`** must be set as an environment variable — never hardcode in properties files.
- **`client_secret_*.json`** (Google OAuth secret) is excluded from git via `.gitignore` — never commit it.
- The `application-dev.properties` contains only the Google Client ID (non-secret) — safe to commit for dev.
- In production, replace CORS allowed origins in `CorsConfig` with your actual frontend domain.
