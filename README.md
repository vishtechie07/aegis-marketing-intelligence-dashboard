# Aegis Intelligence Engine

Real-time competitor intelligence dashboard that ingests multi-source market signals, runs a 3-agent AI analysis pipeline, and streams prioritized insights to an operations UI.

## What This Project Demonstrates

- Production-style full-stack architecture (`Spring Boot + Vue + Postgres`)
- Event-driven UX via Server-Sent Events (SSE) instead of polling
- AI agent orchestration with fallback-safe execution semantics
- Typed backend/frontend contracts (`Java records` <-> `TypeScript interfaces`)
- Dockerized local environment with database migrations and health checks

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.4, WebFlux, Spring AI |
| Data | PostgreSQL 16, Spring Data JPA, Flyway |
| Frontend | Vue 3 (`<script setup lang="ts">`), Vite, Pinia, Tailwind CSS |
| Realtime | SSE (`Flux<ServerSentEvent<T>>` + Vue EventSource composable) |
| Infrastructure | Docker Compose, Nginx (frontend container) |
| Testing | JUnit 5 + AssertJ + Mockito (backend), Vitest + Playwright (frontend) |

## System Architecture

```text
External Signals
  RSS / GDELT / SEC / Reddit / HackerNews / GitHub / Google News / Industry feeds
        |
        v
Scheduled Harvesters (Spring)
  - normalize source payloads
  - save raw article to competitor_news
        |
        +--> Async Agent Orchestration (@Async)
              1) NoiseCancelerAgent -> relevance gate
              2) MarketAnalystAgent -> category classification
              3) StrategistAgent    -> threat score + strategic advice
                    |
                    v
              persist agent_insights
                    |
                    v
              publish InsightEvent to Sinks.Many
                    |
                    v
Frontend Dashboard (SSE subscription via Pinia store)
```

## Core Pipeline Behavior

1. Harvester persists raw data first for traceability.
2. Orchestration runs asynchronously to avoid blocking ingestion.
3. Agents are isolated services; failures degrade gracefully with safe defaults.
4. Final insights are persisted and pushed to the live dashboard stream.

This gives durability (database), observability (history), and low-latency delivery (SSE).

## Repository Structure

```text
.
├─ backend/
│  ├─ src/main/java/com/aegis/
│  │  ├─ agent/        # AI analysis stages
│  │  ├─ harvester/    # source-specific ingestion
│  │  ├─ controller/   # REST + SSE endpoints
│  │  ├─ service/      # orchestration/business logic
│  │  ├─ entity/       # JPA entities
│  │  └─ dto/          # Java record contracts
│  └─ src/main/resources/db/migration/  # Flyway SQL migrations
├─ frontend/
│  ├─ src/components/  # dashboard UI (threat cards, feed, settings)
│  ├─ src/stores/      # Pinia state for insights/settings/competitors
│  ├─ src/composables/ # SSE connection logic
│  └─ src/types/       # DTO mirrors of backend contracts
└─ docker-compose.yml
```

## Quick Start

### Prerequisites

- Docker Desktop (recommended path)
- Or local runtimes: Java 21, Node 20+, PostgreSQL 16
- API keys in `.env` (copy from `.env.example`)

### 1) Configure environment

```bash
cp .env.example .env
```

Set required values (at minimum):

- `POSTGRES_PASSWORD`
- `OPENAI_API_KEY`
- `NEWSAPI_KEY`

Optional:

- `TRACKED_COMPETITORS` (comma-separated)

### 2) Run full stack with Docker

```bash
docker compose up --build
```

Services:

| Service | URL |
|---|---|
| Frontend Dashboard | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Postgres | localhost:5432 |

### 3) Run locally (without Docker)

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## API Surface

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/insights/stream` | Realtime SSE stream of processed insights |
| `GET` | `/api/insights/latest?limit=20` | Paginated latest insights |
| `GET` | `/api/insights/threats?minLevel=7` | Filter high-threat intelligence |
| `POST` | `/api/insights/deep-dive` | Follow-up analysis for a specific news item |
| `GET` | `/actuator/health` | Service health and readiness |

Example deep-dive payload:

```json
{
  "newsId": 123,
  "question": "What does this move imply for our enterprise pricing strategy?"
}
```

## Data Model

Primary tables:

- `competitor_news`: normalized raw harvest output
- `agent_insights`: AI-enriched strategic records referencing `competitor_news`
- `deep_dive_log`: persisted follow-up analysis interactions

Schema evolution is managed via Flyway migrations in `backend/src/main/resources/db/migration`.

## Development and Testing

Backend tests:

```bash
cd backend
./mvnw test
```

Frontend unit tests:

```bash
cd frontend
npm run test
```

Frontend e2e smoke tests:

```bash
cd frontend
npm run test:e2e
```

## Operational Notes

- Backend startup is ordered after Postgres health in `docker-compose.yml` to prevent migration races.
- Harvesters are resilient: API/auth failures are logged, skipped, and retried on next schedule.
- SSE uses a central publisher sink (`Sinks.Many`) as the realtime source of truth.

## Why This Is Useful

Most intelligence dashboards stop at data collection. Aegis adds an AI interpretation layer that turns raw news into:

- relevance-filtered events,
- categorized competitive signals,
- threat-scored strategic actions,

then delivers that stream in real time to support faster product and go-to-market decisions.
