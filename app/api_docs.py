API_REFERENCE = """
# Health Dashboard API

## Authentication

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Login with the fixed password. The response returns a bearer token and also sets an `HttpOnly` cookie for browser access.

Android clients should:

1. Call `POST /api/auth/login`
2. Save `access_token`
3. Send `Authorization: Bearer <token>` on every later request

## Core Endpoints

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
- `POST /api/workouts/parts/{part_id}`
- `PUT /api/workouts/parts/{part_id}`
- `DELETE /api/workouts/parts/{part_id}`
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

## Common Response Notes

- All protected endpoints require either the auth cookie or `Authorization: Bearer <token>`.
- All timestamps are stored and returned with Beijing timezone semantics.
- Validation failures return `422`.
- Missing or invalid authentication returns `401`.
"""
