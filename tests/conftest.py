from __future__ import annotations

import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.auth import AuthManager
from app.main import app, get_store
from app.repository import JsonStore


@pytest.fixture
def temp_store(tmp_path: Path) -> JsonStore:
    return JsonStore(data_dir=tmp_path)


@pytest.fixture
def client(temp_store: JsonStore) -> TestClient:
    app.dependency_overrides[get_store] = lambda: temp_store
    with TestClient(app) as test_client:
        app.state.auth_manager = AuthManager(password="test-pass", secret="test-secret")
        yield test_client
    app.dependency_overrides.clear()
