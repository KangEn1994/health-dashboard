from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time
from dataclasses import dataclass
from typing import Any

from fastapi import HTTPException, Request


def _b64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


@dataclass
class AuthManager:
    password: str
    secret: str
    cookie_name: str = "health_dashboard_session"
    ttl_seconds: int = 30 * 24 * 60 * 60

    def create_token(self) -> str:
        payload = {
            "sub": "health-dashboard",
            "exp": int(time.time()) + self.ttl_seconds,
        }
        payload_bytes = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
        payload_part = _b64url_encode(payload_bytes)
        signature = hmac.new(self.secret.encode("utf-8"), payload_part.encode("utf-8"), hashlib.sha256).digest()
        return f"{payload_part}.{_b64url_encode(signature)}"

    def verify_token(self, token: str) -> dict[str, Any]:
        try:
            payload_part, signature_part = token.split(".", 1)
        except ValueError as exc:
            raise HTTPException(status_code=401, detail="invalid token") from exc

        expected_signature = hmac.new(
            self.secret.encode("utf-8"),
            payload_part.encode("utf-8"),
            hashlib.sha256,
        ).digest()
        actual_signature = _b64url_decode(signature_part)
        if not hmac.compare_digest(expected_signature, actual_signature):
            raise HTTPException(status_code=401, detail="invalid token")

        payload = json.loads(_b64url_decode(payload_part).decode("utf-8"))
        if payload.get("exp", 0) < int(time.time()):
            raise HTTPException(status_code=401, detail="token expired")
        return payload

    def verify_password(self, password: str) -> None:
        if password != self.password:
            raise HTTPException(status_code=401, detail="invalid password")

    def token_from_request(self, request: Request) -> str | None:
        header = request.headers.get("Authorization", "")
        if header.lower().startswith("bearer "):
            return header.split(" ", 1)[1].strip()
        return request.cookies.get(self.cookie_name)

    def require_request_auth(self, request: Request) -> dict[str, Any]:
        token = self.token_from_request(request)
        if not token:
            raise HTTPException(status_code=401, detail="authentication required")
        return self.verify_token(token)


def build_auth_manager() -> AuthManager:
    password = os.getenv("HEALTH_DASHBOARD_PASSWORD", "19940318")
    secret = os.getenv("HEALTH_DASHBOARD_TOKEN_SECRET", password)
    return AuthManager(password=password, secret=secret)
