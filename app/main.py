from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import Depends, FastAPI, Query, Request, Response
from fastapi.openapi.utils import get_openapi
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from fastapi.openapi.docs import get_swagger_ui_html

from app.api_docs import API_REFERENCE
from app.auth import AuthManager, build_auth_manager
from app.repository import JsonStore
from app.schemas import EntryCreate, EntryUpdate, LoginRequest, MetricCreate, MetricUpdate, ProfileUpdate
from app.schemas import (
    WorkoutExerciseCreate,
    WorkoutExerciseUpdate,
    WorkoutPartCreate,
    WorkoutPartUpdate,
    WorkoutPlanCreate,
    WorkoutPlanUpdate,
    WorkoutSessionCreate,
    WorkoutSessionUpdate,
)
from app.services import DashboardService


BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"


@asynccontextmanager
async def lifespan(application: FastAPI):
    application.state.store = JsonStore()
    application.state.auth_manager = build_auth_manager()
    yield


app = FastAPI(title="Health Dashboard", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


def get_store() -> JsonStore:
    return app.state.store


def get_auth_manager() -> AuthManager:
    return app.state.auth_manager


def get_service(store: JsonStore = Depends(get_store)) -> DashboardService:
    return DashboardService(store)


def require_api_auth(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> dict:
    return auth_manager.require_request_auth(request)


def ensure_page_auth(request: Request, auth_manager: AuthManager) -> bool:
    try:
        auth_manager.require_request_auth(request)
        return True
    except Exception:
        return False


@app.get("/api/health")
def health_check() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/auth/login")
def login(
    payload: LoginRequest,
    response: Response,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> dict[str, str | int]:
    auth_manager.verify_password(payload.password)
    token = auth_manager.create_token()
    response.set_cookie(
        key=auth_manager.cookie_name,
        value=token,
        httponly=True,
        samesite="lax",
        max_age=auth_manager.ttl_seconds,
        path="/",
    )
    return {"access_token": token, "token_type": "bearer", "expires_in": auth_manager.ttl_seconds}


@app.post("/api/auth/logout")
def logout(
    response: Response,
    _auth: dict = Depends(require_api_auth),
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> dict[str, str]:
    response.delete_cookie(auth_manager.cookie_name, path="/")
    return {"status": "logged_out"}


@app.get("/api/auth/me")
def auth_me(_auth: dict = Depends(require_api_auth)) -> dict[str, str]:
    return {"status": "authenticated"}


@app.get("/api/profile")
def get_profile(_auth: dict = Depends(require_api_auth), service: DashboardService = Depends(get_service)) -> dict:
    return service.get_profile()


@app.put("/api/profile")
def update_profile(
    payload: ProfileUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_profile(payload)


@app.get("/api/metrics")
def get_metrics(_auth: dict = Depends(require_api_auth), service: DashboardService = Depends(get_service)) -> list[dict]:
    return service.get_metrics()


@app.post("/api/metrics", status_code=201)
def create_metric(
    payload: MetricCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_metric(payload)


@app.put("/api/metrics/{metric_id}")
def update_metric(
    metric_id: str,
    payload: MetricUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_metric(metric_id, payload)


@app.delete("/api/metrics/{metric_id}")
def delete_metric(
    metric_id: str,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.archive_metric(metric_id)


@app.get("/api/entries")
def get_entries(
    start_date: str | None = Query(default=None),
    end_date: str | None = Query(default=None),
    query: str | None = Query(default=None),
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> list[dict]:
    return service.get_entries(start_date, end_date, query)


@app.post("/api/entries", status_code=201)
def create_entry(
    payload: EntryCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_entry(payload)


@app.put("/api/entries/{entry_id}")
def update_entry(
    entry_id: str,
    payload: EntryUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_entry(entry_id, payload)


@app.delete("/api/entries/{entry_id}")
def delete_entry(
    entry_id: str,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.delete_entry(entry_id)


@app.get("/api/dashboard")
def dashboard(
    range: str = Query(default="month", pattern="^(month|quarter|year|30d|90d|365d|all)$"),
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.dashboard(range)


@app.get("/api/analytics")
def metric_analytics(
    range: str = Query(default="month", pattern="^(month|quarter|year|30d|90d|365d|all)$"),
    metrics: str = Query(default="weight_kg,body_fat_pct"),
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    metric_ids = [item.strip() for item in metrics.split(",") if item.strip()]
    return service.analytics(range, metric_ids)


@app.get("/api/workouts/overview")
def workout_overview(
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.workout_overview()


@app.get("/api/workouts/catalog")
def get_workout_catalog(
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.get_workout_catalog()


@app.post("/api/workouts/parts/{part_id}", status_code=201)
def create_workout_part(
    part_id: str,
    payload: WorkoutPartCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_workout_part(part_id, payload)


@app.put("/api/workouts/parts/{part_id}")
def update_workout_part(
    part_id: str,
    payload: WorkoutPartUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_workout_part(part_id, payload)


@app.post("/api/workouts/parts/{part_id}/exercises/{exercise_id}", status_code=201)
def create_workout_exercise(
    part_id: str,
    exercise_id: str,
    payload: WorkoutExerciseCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_workout_exercise(part_id, exercise_id, payload)


@app.put("/api/workouts/parts/{part_id}/exercises/{exercise_id}")
def update_workout_exercise(
    part_id: str,
    exercise_id: str,
    payload: WorkoutExerciseUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_workout_exercise(part_id, exercise_id, payload)


@app.get("/api/workouts/plans")
def get_workout_plans(
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> list[dict]:
    return service.get_workout_plans()


@app.post("/api/workouts/plans", status_code=201)
def create_workout_plan(
    payload: WorkoutPlanCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_workout_plan(payload)


@app.put("/api/workouts/plans/{plan_id}")
def update_workout_plan(
    plan_id: str,
    payload: WorkoutPlanUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_workout_plan(plan_id, payload)


@app.delete("/api/workouts/plans/{plan_id}")
def delete_workout_plan(
    plan_id: str,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.delete_workout_plan(plan_id)


@app.get("/api/workouts/sessions")
def get_workout_sessions(
    start_date: str | None = Query(default=None),
    end_date: str | None = Query(default=None),
    query: str | None = Query(default=None),
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> list[dict]:
    return service.get_workout_sessions(start_date, end_date, query)


@app.post("/api/workouts/sessions", status_code=201)
def create_workout_session(
    payload: WorkoutSessionCreate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.create_workout_session(payload)


@app.put("/api/workouts/sessions/{session_id}")
def update_workout_session(
    session_id: str,
    payload: WorkoutSessionUpdate,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.update_workout_session(session_id, payload)


@app.delete("/api/workouts/sessions/{session_id}")
def delete_workout_session(
    session_id: str,
    _auth: dict = Depends(require_api_auth),
    service: DashboardService = Depends(get_service),
) -> dict:
    return service.delete_workout_session(session_id)


@app.get("/api/openapi.json")
def protected_openapi(
    _auth: dict = Depends(require_api_auth),
) -> JSONResponse:
    schema = get_openapi(title=app.title, version="1.0.0", routes=app.routes)
    return JSONResponse(schema)


@app.get("/api/reference", response_class=HTMLResponse)
def api_reference_page(_auth: dict = Depends(require_api_auth)) -> str:
    return f"<pre>{API_REFERENCE}</pre>"


@app.get("/docs", response_model=None)
def docs_page(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return get_swagger_ui_html(openapi_url="/api/openapi.json", title="Health Dashboard API Docs")


@app.get("/login")
def login_page() -> FileResponse:
    return FileResponse(STATIC_DIR / "login.html")


@app.get("/", response_model=None)
def index(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/records", response_model=None)
def records_page(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return FileResponse(STATIC_DIR / "records.html")


@app.get("/metrics", response_model=None)
def metrics_page(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return FileResponse(STATIC_DIR / "metrics.html")


@app.get("/workouts", response_model=None)
def workouts_page(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return FileResponse(STATIC_DIR / "workouts.html")


@app.get("/workout-settings", response_model=None)
def workout_settings_page(
    request: Request,
    auth_manager: AuthManager = Depends(get_auth_manager),
) -> Response:
    if not ensure_page_auth(request, auth_manager):
        return RedirectResponse(url="/login", status_code=302)
    return FileResponse(STATIC_DIR / "workout-settings.html")
