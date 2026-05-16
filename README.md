# Health Dashboard

A self-hosted health dashboard for manually maintaining body metrics such as weight and body fat.

## Features

- FastAPI backend with static HTML frontend
- JSON file storage with no database
- Dashboard, records management, and metrics configuration pages
- Workout catalog, grouped workout plans, and workout session tracking
- Dynamic metric definitions and derived BMI analysis
- Docker Compose deployment

## Project Structure

```text
app/
docker/
stack/
tests/
requirements.txt
README.md
```

## Local Development

```bash
cd /Users/kang_en/codex/health-dashboard
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Open [http://localhost:8000](http://localhost:8000).

## Data Files

Runtime data is stored in `stack/data` for Docker and `app/data` by default when running locally:

- `profile.json`
- `metrics.json`
- `entries.json`
- `workout_catalog.json`
- `workout_plans.json`
- `workout_sessions.json`

You can edit these files manually. The app reloads them automatically when they change on disk.

## Docker

```bash
cd /Users/kang_en/codex/health-dashboard/stack
docker compose up --build -d
```

Open [http://localhost:18080](http://localhost:18080).

## Authentication

The app now uses a fixed password for both the browser UI and future Android clients.

1. Copy `/Users/kang_en/codex/health-dashboard/.env.example`
2. Default password is `19940318`, or set your own `HEALTH_DASHBOARD_PASSWORD`
3. Set `HEALTH_DASHBOARD_TOKEN_SECRET`
4. Restart the stack

Browser login uses an `HttpOnly` cookie. API clients such as Android should call `POST /api/auth/login` and then send `Authorization: Bearer <token>`.

## API Overview

### Auth

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Example login request:

```json
{
  "password": "your-fixed-password"
}
```

Example login response:

```json
{
  "access_token": "token",
  "token_type": "bearer",
  "expires_in": 2592000
}
```

### Health

- `GET /api/health`

### Profile

- `GET /api/profile`
- `PUT /api/profile`

### Metrics

- `GET /api/metrics`
- `POST /api/metrics`
- `PUT /api/metrics/{metric_id}`
- `DELETE /api/metrics/{metric_id}`

### Entries

- `GET /api/entries?start_date=YYYY-MM-DD&end_date=YYYY-MM-DD&query=keyword`
- `POST /api/entries`
- `PUT /api/entries/{entry_id}`
- `DELETE /api/entries/{entry_id}`

### Dashboard / Analytics

- `GET /api/dashboard?range=month|quarter|year`
- `GET /api/analytics?range=month|quarter|year&metrics=weight_kg,body_fat_pct`

### Workouts

- `GET /api/workouts/overview`
- `GET /api/workouts/catalog`
- `POST /api/workouts/parts`
- `POST /api/workouts/parts/{part_id}`
- `PUT /api/workouts/parts/{part_id}`
- `DELETE /api/workouts/parts/{part_id}`
- `POST /api/workouts/parts/{part_id}/exercises`
- `POST /api/workouts/parts/{part_id}/exercises/{exercise_id}`
- `PUT /api/workouts/parts/{part_id}/exercises/{exercise_id}`
- `DELETE /api/workouts/parts/{part_id}/exercises/{exercise_id}`
- `GET /api/workouts/plans`
- `POST /api/workouts/plans`
- `PUT /api/workouts/plans/{plan_id}`
- `DELETE /api/workouts/plans/{plan_id}`
- `GET /api/workouts/sessions?start_date=YYYY-MM-DD&end_date=YYYY-MM-DD&query=keyword`
- `POST /api/workouts/sessions`
- `PUT /api/workouts/sessions/{session_id}`
- `DELETE /api/workouts/sessions/{session_id}`

### API Docs

- Browser docs: [http://localhost:18080/docs](http://localhost:18080/docs)
- Protected reference text endpoint: `GET /api/reference`

All endpoints except `/api/health`, `/api/auth/login`, and `/login` require authentication.
