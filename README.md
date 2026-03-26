# Aegis Intelligence Engine

A real-time competitor intelligence platform that harvests news, processes it through a 3-stage Spring AI agent pipeline, and streams insights to a Vue 3 dashboard via Server-Sent Events.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.4, WebFlux, Spring AI (OpenAI) |
| Database | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Frontend | Vue 3, Vite, TypeScript, Tailwind CSS 4, Pinia |
| Infrastructure | Docker Compose |

## Architecture

```
RSS / GDELT / SEC / Reddit / HN / GitHub / Google News / Yahoo / USASpending / World Bank
    → Harvester (Spring Scheduler) → PostgreSQL
                                   → Agent Pipeline (Async)
                                       ├── NoiseCanceler
                                       ├── MarketAnalyst
                                       └── Strategist → SSE Sink → Vue Dashboard
```

## Quick Start

### Prerequisites
- Docker Desktop (optional; backend can run against local Postgres)
- OpenAI API key: set via **Settings** in the dashboard (not stored in repo or logs)

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env if using Docker (e.g. POSTGRES_PASSWORD; OpenAI key via UI)
```

### 2. Run with Docker

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Dashboard | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Postgres | localhost:5432 |

### 3. Local development (without Docker)

**Backend** — requires PostgreSQL running locally:

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend:**

```bash
cd frontend
npm install
npm run dev
```

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/insights/stream` | SSE stream of real-time insights |
| `GET` | `/api/insights/latest?limit=20` | Most recent N insights |
| `GET` | `/api/insights/threats?minLevel=7` | High-threat insights |
| `POST` | `/api/insights/deep-dive` | Ask Agent deep-dive analysis |
| `GET` | `/actuator/health` | Health check |

### Deep Dive Request

```json
POST /api/insights/deep-dive
{ "newsId": 123, "question": "What does this mean for our pricing strategy?" }
```

## Agent Pipeline

1. **NoiseCanceler** — filters PR fluff and generic industry roundups
2. **MarketAnalyst** — categorizes into `PRODUCT_LAUNCH | HIRING | FINANCIAL_MOVE | PARTNERSHIP | LEGAL | LEADERSHIP_CHANGE`
3. **Strategist** — assigns a threat level (1-10) and generates a one-sentence strategic action

## Database Schema

```sql
competitor_news   -- raw harvested articles
agent_insights    -- AI-processed insights (FK → competitor_news)
```

Migrations managed by Flyway in `backend/src/main/resources/db/migration/`.

## Key Metrics

- News → AI analysis latency: target < 2.5 seconds
- Noise reduction: ~60% of articles filtered by NoiseCanceler
- Scalability: Virtual Threads enabled for concurrent competitor tracking

## Environment Variables

See `.env.example` for all configuration options.
