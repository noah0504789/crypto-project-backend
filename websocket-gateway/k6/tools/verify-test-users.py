#!/usr/bin/env python3
"""발급한 토큰이 실제로 검증되는지 오프라인으로 확인한다.

게이트웨이는 JWKS 로 서명을 검증한다. 서비스를 다 띄운 뒤에야 알게 되면 비싸므로,
Vault 가 들고 있는 같은 키의 공개키로 여기서 미리 확인한다.
검증은 openssl 에 맡긴다(RS256 = RSASSA-PKCS1-v1_5 + SHA-256).

사용법: tools/verify-test-users.py [토큰파일]
"""

import base64
import json
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from mint_helpers import KEY_NAME, KEY_VERSION, VAULT_ADDR, get_json, load_vault_token


def b64u_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def fetch_public_key(vault_token: str) -> str:
    result = get_json(
        f"{VAULT_ADDR}/v1/transit/keys/{KEY_NAME}",
        {"X-Vault-Token": vault_token},
    )
    keys = result.get("data", {}).get("keys", {})
    entry = keys.get(str(KEY_VERSION))
    if not entry:
        sys.exit(f"Vault 에 {KEY_NAME}:{KEY_VERSION} 공개키가 없다")

    return entry["public_key"] if isinstance(entry, dict) else entry


def verify(token: str, pem_path: Path) -> bool:
    header_b64u, payload_b64u, signature_b64u = token.split(".")

    with tempfile.TemporaryDirectory() as workspace:
        signature_path = Path(workspace) / "sig"
        signature_path.write_bytes(b64u_decode(signature_b64u))

        completed = subprocess.run(
            [
                "openssl", "dgst", "-sha256",
                "-verify", str(pem_path),
                "-signature", str(signature_path),
            ],
            input=f"{header_b64u}.{payload_b64u}".encode("ascii"),
            capture_output=True,
        )

    return completed.returncode == 0


def main() -> None:
    accounts_dir = Path(__file__).resolve().parent.parent / "accounts"
    token_file = accounts_dir / (sys.argv[1] if len(sys.argv) > 1 else "test-users.json")
    if not token_file.exists():
        sys.exit(f"토큰 파일 없음: {token_file}")

    users = json.loads(token_file.read_text())

    public_key_pem = fetch_public_key(load_vault_token())

    with tempfile.TemporaryDirectory() as workspace:
        pem_path = Path(workspace) / "pub.pem"
        pem_path.write_text(public_key_pem)

        failures = [user["userId"] for user in users if not verify(user["token"], pem_path)]

    now = int(time.time())
    expiries = {json.loads(b64u_decode(user["token"].split(".")[1]))["exp"] for user in users}
    earliest = min(expiries)

    print(f"검증  {len(users) - len(failures)}/{len(users)} 통과")
    print(f"만료  {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(earliest))}"
          f" (남은 {(earliest - now) // 86400}일)")

    if failures:
        print(f"실패  {failures[:5]}")
        sys.exit(1)


if __name__ == "__main__":
    main()
