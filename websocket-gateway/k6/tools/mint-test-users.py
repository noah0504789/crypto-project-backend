#!/usr/bin/env python3
"""부하테스트용 테스트 계정 N개와 그 access token 을 발급한다.

user DB 에는 행을 만들지 않는다. 채팅 쓰기 경로가 보는 것은
chat_room.memberIds 안에 writerId 가 있는지 뿐이라, 계정 실체 없이
UUID 와 그 UUID 를 id 클레임에 담은 토큰만 있으면 된다.
방 멤버 등록은 seed-room-members.sh 가 한다.

서명은 운영과 같은 Vault transit 키로 한다(config-server 의 /sign 과 같은 절차).
config-server 를 띄우지 않아도 되도록 Vault 를 직접 호출할 뿐, 알고리즘·키·kid 는 같다.

사용법:
    tools/mint-test-users.py [개수] [출력파일]
    tools/mint-test-users.py 300 test-users.json
"""

import base64
import hashlib
import json
import os
import sys
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mint_helpers import KEY_NAME, KEY_VERSION, VAULT_ADDR, load_vault_token, post_json

ACCOUNTS_DIR = Path(__file__).resolve().parent.parent / "accounts"

ISSUER = os.environ.get("JWT_ISSUER", "http://crypto-oauth2-authorization-server:9000")
AUDIENCE = os.environ.get("JWT_AUDIENCE", "my-client-id")
TTL_SECONDS = int(os.environ.get("TOKEN_TTL_SECONDS", str(5 * 365 * 24 * 3600)))


def b64u(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def vault_sign(vault_token: str, signing_input: str) -> str:
    """config-server 와 같은 절차: 서명 입력을 SHA-256 으로 요약해 prehashed 로 넘긴다."""
    digest = hashlib.sha256(signing_input.encode("ascii")).digest()

    result = post_json(
        f"{VAULT_ADDR}/v1/transit/sign/{KEY_NAME}",
        {
            "input": base64.b64encode(digest).decode(),
            "prehashed": True,
            "hash_algorithm": "sha2-256",
            "signature_algorithm": "pkcs1v15",
            "key_version": KEY_VERSION,
        },
        {"X-Vault-Token": vault_token},
    )

    signature = result.get("data", {}).get("signature")
    if not signature:
        sys.exit("Vault 서명 응답이 비어 있다")

    # "vault:v1:<base64>" 에서 뒤쪽만 떼어 base64url 로 바꾼다.
    return b64u(base64.b64decode(signature.rsplit(":", 1)[1]))


def main() -> None:
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 300
    ACCOUNTS_DIR.mkdir(mode=0o700, exist_ok=True)
    out_path = ACCOUNTS_DIR / (sys.argv[2] if len(sys.argv) > 2 else "test-users.json")

    vault_token = load_vault_token()

    header_b64u = b64u(
        json.dumps(
            {"alg": "RS256", "typ": "JWT", "kid": f"{KEY_NAME}:{KEY_VERSION}"},
            separators=(",", ":"),
        ).encode()
    )

    now = int(time.time())
    exp = now + TTL_SECONDS

    users = []
    for index in range(1, count + 1):
        user_id = str(uuid.uuid4())

        payload_b64u = b64u(
            json.dumps(
                {
                    "sub": f"loadtest-{index:04d}@loadtest.local",
                    "aud": [AUDIENCE],
                    "nbf": now,
                    "roles": ["ROLE_USER"],
                    "iss": ISSUER,
                    "id": user_id,
                    "exp": exp,
                    "iat": now,
                    "jti": str(uuid.uuid4()),
                },
                separators=(",", ":"),
            ).encode()
        )

        signing_input = f"{header_b64u}.{payload_b64u}"
        signature = vault_sign(vault_token, signing_input)

        users.append({"userId": user_id, "token": f"{signing_input}.{signature}"})

        if index % 50 == 0:
            print(f"  {index}/{count}", flush=True)

    out_path.write_text(json.dumps(users, indent=2))
    out_path.chmod(0o600)

    ids_path = out_path.with_name(out_path.stem + "-ids.txt")
    ids_path.write_text("\n".join(user["userId"] for user in users) + "\n")

    print(f"발급 {len(users)}개")
    print(f"  토큰  {out_path}")
    print(f"  ID    {ids_path}")
    print(f"  만료  {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(exp))}")


if __name__ == "__main__":
    main()
