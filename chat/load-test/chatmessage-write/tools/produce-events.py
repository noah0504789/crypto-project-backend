#!/usr/bin/env python3
import argparse
import hashlib
import json
import re
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path


EVENT_TYPE = "org.example.chat.chatmessage.application.event.ChatMessagePersistEvent"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="chatmessage-event 직접 부하 발행기")
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--rate", type=float, required=True, help="초당 발행 건수")
    parser.add_argument("--room-id", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--members-file", type=Path, required=True)
    parser.add_argument("--writer-count", type=int, default=100)
    return parser.parse_args()


def validate(args: argparse.Namespace, member_ids: list[str]) -> None:
    if args.count <= 0:
        raise ValueError("count는 1 이상이어야 한다")
    if args.count > 0xFFFFFF:
        raise ValueError("count는 ObjectId sequence 범위(16,777,215)를 넘을 수 없다")
    if args.rate <= 0:
        raise ValueError("rate는 0보다 커야 한다")
    if not re.fullmatch(r"[0-9a-fA-F]{24}", args.room_id):
        raise ValueError("room-id는 24자리 ObjectId hex여야 한다")
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.run_id):
        raise ValueError("run-id는 영문·숫자·_·-만 사용할 수 있다")
    if not member_ids:
        raise ValueError("테스트 방에 멤버가 없다")
    if args.writer_count <= 0 or args.writer_count > len(member_ids):
        raise ValueError("writer-count는 1 이상, 방 멤버 수 이하여야 한다")


def message_id(run_id: str, sequence: int, started_at: int) -> str:
    run_hash = hashlib.sha256(run_id.encode()).hexdigest()[:10]
    return f"{started_at:08x}{run_hash}{sequence:06x}"


def main() -> None:
    args = parse_args()
    member_ids = json.loads(args.members_file.read_text())
    validate(args, member_ids)

    started_at_epoch = int(time.time())
    started_at = time.monotonic()
    writers = member_ids[:args.writer_count]

    for index in range(args.count):
        target = started_at + index / args.rate
        remaining = target - time.monotonic()
        if remaining > 0:
            time.sleep(remaining)

        sequence = index + 1
        event_id = str(uuid.uuid4())
        transaction_id = str(uuid.uuid4())
        payload = {
            "payload": {
                "id": message_id(args.run_id, sequence, started_at_epoch),
                "roomId": args.room_id,
                "writerId": writers[index % len(writers)],
                "content": f"chatmessage-write-benchmark:{args.run_id}:{sequence}",
                "createdAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            },
            "memberIds": member_ids,
        }
        headers = f"__TypeId__:{EVENT_TYPE},event_id:{event_id},transaction_id:{transaction_id}"
        print(f"{headers}\t{args.room_id}\t{json.dumps(payload, separators=(',', ':'))}", flush=True)

    elapsed = time.monotonic() - started_at
    print(f"published={args.count} elapsed_seconds={elapsed:.3f} rate={args.count / elapsed:.2f}", file=sys.stderr)


if __name__ == "__main__":
    main()
