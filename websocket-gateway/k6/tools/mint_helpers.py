"""발급과 검증이 함께 쓰는 Vault 접근 조각."""

import json
import os
import sys
import urllib.request
from pathlib import Path

SERVICE_ENV_FILE = Path(
    os.environ.get(
        "SERVICE_ENV_FILE",
        Path.home() / "crypto-project/crypto-project-infra/service/.env",
    )
)

VAULT_ADDR = os.environ.get("VAULT_ADDR", "http://localhost:18200")
KEY_NAME = os.environ.get("JWT_KEY_NAME", "my-authorization-server-jwt")
KEY_VERSION = int(os.environ.get("JWT_KEY_VERSION", "1"))


def read_env(path: Path) -> dict:
    """.env 를 읽는다. 값은 출력하지 않는다."""
    if not path.exists():
        sys.exit(f"환경 파일 없음: {path}")

    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip().strip("'\"")
    return values


def _request(url: str, headers: dict, data: bytes = None, method: str = "GET") -> dict:
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", **headers},
        method=method,
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def get_json(url: str, headers: dict = None) -> dict:
    return _request(url, headers or {})


def post_json(url: str, body: dict, headers: dict = None) -> dict:
    return _request(url, headers or {}, json.dumps(body).encode(), "POST")


def load_vault_token() -> str:
    """service/.env 의 AppRole 로 로그인해 Vault 토큰을 얻는다."""
    env = read_env(SERVICE_ENV_FILE)
    role_id = env.get("VAULT_ROLE_ID")
    secret_id = env.get("VAULT_SECRET_ID")

    if not role_id or not secret_id:
        sys.exit(f"{SERVICE_ENV_FILE} 에 VAULT_ROLE_ID / VAULT_SECRET_ID 가 없다")

    result = post_json(
        f"{VAULT_ADDR}/v1/auth/approle/login",
        {"role_id": role_id, "secret_id": secret_id},
    )

    token = result.get("auth", {}).get("client_token")
    if not token:
        sys.exit("Vault AppRole 로그인 실패")

    return token
