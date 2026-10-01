"""Thin async client for the local Hermes API server. The bearer key never leaves this module."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any, AsyncIterator

import httpx

from .config import read_hermes_api_key


class HermesError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


class HermesClient:
    def __init__(self, base_url: str, env_path: Path):
        self._env_path = env_path
        self._key_mtime: float | None = None
        self._key = ""
        self._http = httpx.AsyncClient(base_url=base_url, timeout=httpx.Timeout(30.0, connect=5.0))

    def _auth(self) -> dict[str, str]:
        mtime = os.stat(self._env_path).st_mtime
        if mtime != self._key_mtime:  # pick up key rotation without a restart
            self._key = read_hermes_api_key(self._env_path)
            self._key_mtime = mtime
        return {"Authorization": f"Bearer {self._key}"}

    async def aclose(self) -> None:
        await self._http.aclose()

    async def request(self, method: str, path: str, *, json: Any = None, params: dict | None = None,
                      headers: dict | None = None, ok: tuple[int, ...] = (200, 201, 202)) -> httpx.Response:
        try:
            resp = await self._http.request(method, path, json=json, params=params,
                                            headers={**self._auth(), **(headers or {})})
        except httpx.HTTPError as exc:
            raise HermesError(502, "hermes_unavailable", f"Hermes API unreachable ({type(exc).__name__})") from exc
        if resp.status_code not in ok:
            code, message = "hermes_error", f"Hermes returned HTTP {resp.status_code}"
            try:
                err = resp.json().get("error") or {}
                if isinstance(err, dict):
                    code = err.get("code") or code
                    message = err.get("message") or message
                elif isinstance(err, str):
                    message = err
            except ValueError:
                pass
            status = resp.status_code if resp.status_code in (400, 404, 409, 413, 422, 429) else 502
            raise HermesError(status, code, message)
        return resp

    async def open_run_events(self, run_id: str) -> AsyncIterator[str]:
        """Open the upstream SSE stream; returns an async line iterator that owns the response."""
        req = self._http.build_request("GET", f"/v1/runs/{run_id}/events", headers=self._auth(),
                                       timeout=httpx.Timeout(None, connect=5.0))
        resp = await self._http.send(req, stream=True)
        if resp.status_code != 200:
            await resp.aclose()
            raise HermesError(resp.status_code, "run_events_unavailable", f"HTTP {resp.status_code}")

        async def lines() -> AsyncIterator[str]:
            try:
                async for line in resp.aiter_lines():
                    yield line
            finally:
                await resp.aclose()

        return lines()

    async def run_status(self, run_id: str) -> dict | None:
        try:
            resp = await self.request("GET", f"/v1/runs/{run_id}", ok=(200,))
        except HermesError as exc:
            if exc.status == 404:
                return None
            raise
        return resp.json()

    async def health(self) -> dict:
        resp = await self.request("GET", "/health/detailed", ok=(200,))
        return resp.json()
