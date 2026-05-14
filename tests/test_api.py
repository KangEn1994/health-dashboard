from __future__ import annotations

from datetime import UTC, datetime, timedelta


def create_entry_payload(days_ago: int, weight: float, body_fat: float) -> dict:
    recorded_at = datetime.now(UTC) - timedelta(days=days_ago)
    return {
        "recorded_at": recorded_at.isoformat(),
        "values": {
            "weight_kg": weight,
            "body_fat_pct": body_fat,
            "waist_cm": 82 - days_ago * 0.2,
        },
        "note": "test",
        "tags": ["weekly"],
    }


def auth_headers(client) -> dict[str, str]:
    response = client.post("/api/auth/login", json={"password": "test-pass"})
    assert response.status_code == 200
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_static_pages_load(client) -> None:
    assert client.get("/").status_code == 200
    assert client.get("/records").status_code == 200
    assert client.get("/metrics").status_code == 200
    assert client.get("/login").status_code == 200


def test_login_and_auth_me(client) -> None:
    login = client.post("/api/auth/login", json={"password": "test-pass"})
    assert login.status_code == 200
    assert "access_token" in login.json()
    me = client.get("/api/auth/me")
    assert me.status_code == 200
    assert me.json()["status"] == "authenticated"


def test_protected_endpoint_requires_auth(client) -> None:
    fresh = client.__class__(client.app)
    response = fresh.get("/api/profile")
    assert response.status_code == 401


def test_metric_crud(client) -> None:
    headers = auth_headers(client)
    response = client.post(
        "/api/metrics",
        json={
            "id": "water_intake_l",
            "label": "饮水量",
            "unit": "L",
            "type": "number",
            "color": "#14b8a6",
            "chart_type": "bar",
            "precision": 1,
            "goal_direction": "up",
            "active": True,
            "show_on_dashboard": True,
            "sort_order": 70,
        },
        headers=headers,
    )
    assert response.status_code == 201
    duplicate = client.post("/api/metrics", json=response.json(), headers=headers)
    assert duplicate.status_code == 422
    update = client.put(
        "/api/metrics/water_intake_l",
        json={
            "label": "日饮水量",
            "unit": "L",
            "type": "number",
            "color": "#14b8a6",
            "chart_type": "bar",
            "precision": 1,
            "goal_direction": "up",
            "active": True,
            "show_on_dashboard": False,
            "sort_order": 71,
        },
        headers=headers,
    )
    assert update.status_code == 200
    archived = client.delete("/api/metrics/water_intake_l", headers=headers)
    assert archived.status_code == 200
    assert archived.json()["active"] is False


def test_entry_crud_and_dashboard(client) -> None:
    headers = auth_headers(client)
    first = client.post("/api/entries", json=create_entry_payload(3, 72.4, 18.2), headers=headers)
    second = client.post("/api/entries", json=create_entry_payload(0, 71.8, 17.6), headers=headers)
    assert first.status_code == 201
    assert second.status_code == 201

    entries = client.get("/api/entries", headers=headers)
    assert entries.status_code == 200
    assert len(entries.json()) == 2

    dashboard = client.get("/api/dashboard?range=30d", headers=headers)
    assert dashboard.status_code == 200
    payload = dashboard.json()
    assert payload["summaries"]["weight_kg"]["latest"] == 71.8
    assert payload["summaries"]["bmi"]["latest"] is not None
    assert payload["continuity"]["record_days"] == 2
    assert payload["trends"]["weight_kg"][0]["recorded_at"].endswith("+08:00")
    assert any("本" in insight for insight in payload["insights"])

    analytics = client.get("/api/analytics?range=30d&metrics=weight_kg,body_fat_pct", headers=headers)
    assert analytics.status_code == 200
    assert "weight_kg" in analytics.json()["metrics"]

    created_id = entries.json()[0]["id"]
    updated = client.put(
        f"/api/entries/{created_id}",
        json={
            "recorded_at": datetime.now(UTC).isoformat(),
            "values": {
                "weight_kg": 71.5,
                "body_fat_pct": 17.1,
            },
            "note": "updated",
            "tags": ["updated"],
        },
        headers=headers,
    )
    assert updated.status_code == 200

    deleted = client.delete(f"/api/entries/{created_id}", headers=headers)
    assert deleted.status_code == 200


def test_invalid_metric_in_entry_rejected(client) -> None:
    headers = auth_headers(client)
    response = client.post(
        "/api/entries",
        json={
            "recorded_at": datetime.now(UTC).isoformat(),
            "values": {"unknown_metric": 1},
            "note": "",
            "tags": [],
        },
        headers=headers,
    )
    assert response.status_code == 422


def test_entry_filter_uses_beijing_date(client) -> None:
    headers = auth_headers(client)
    response = client.post(
        "/api/entries",
        json={
            "recorded_at": "2026-05-14T00:30:00+08:00",
            "values": {"weight_kg": 70.2},
            "note": "bj date",
            "tags": [],
        },
        headers=headers,
    )
    assert response.status_code == 201
    entries = client.get("/api/entries?start_date=2026-05-14&end_date=2026-05-14", headers=headers)
    assert entries.status_code == 200
    assert len(entries.json()) == 1


def test_dashboard_supports_named_ranges(client) -> None:
    headers = auth_headers(client)
    for range_name in ("month", "quarter", "year"):
        response = client.get(f"/api/dashboard?range={range_name}", headers=headers)
        assert response.status_code == 200
