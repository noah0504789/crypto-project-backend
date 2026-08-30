#!/usr/bin/env python3
"""sample-metrics.sh 가 쌓은 파일을 회차 요약으로 줄인다.

카운터는 회차 동안의 증가량, 게이지는 최댓값을 본다.
지연과 달리 이 값들은 스왑에 흔들리지 않아 회차 간 비교의 근거가 된다.

사용법: tools/summarize-metrics.py results/run-vu100-....metrics
"""

import re
import sys
from collections import defaultdict
from pathlib import Path

LINE = re.compile(r"^(\d+) (\w+) ([a-z_:]+)(\{[^}]*\})? (.+)$")

# 카운터는 증가량, 게이지는 최댓값으로 본다.
COUNTERS = (
    "stomp_executor_rejected_total",
    "chat_badge_coalesced_total",
    "chat_badge_flushed_total",
    "chat_message_batch_buffered_total",
    "chat_message_batch_frames_total",
    "chat_message_batch_overflow_total",
    "chat_message_ack_direct_sent_total",
    "chat_message_ack_direct_fallback_total",
    "executor_completed_tasks_total",
)

GAUGES = (
    "executor_queued_tasks",
    "executor_active_threads",
    "chat_badge_pending",
    "chat_message_batch_pending_rooms",
    "ws_active_sessions",
)


def parse(path: Path):
    counters = defaultdict(lambda: [None, None])  # series -> [first, last]
    gauges = defaultdict(float)

    for raw in path.read_text().splitlines():
        matched = LINE.match(raw)
        if not matched:
            continue

        _, source, name, labels, value = matched.groups()

        try:
            value = float(value.split()[0])
        except ValueError:
            continue

        series = f"{source} {name}{labels or ''}"

        if name in COUNTERS:
            slot = counters[series]
            if slot[0] is None:
                slot[0] = value
            slot[1] = value
        elif name in GAUGES:
            gauges[series] = max(gauges[series], value)

    return counters, gauges


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("사용법: tools/summarize-metrics.py <샘플파일>")

    path = Path(sys.argv[1])
    if not path.exists():
        sys.exit(f"파일 없음: {path}")

    counters, gauges = parse(path)

    print("=== 카운터 증가량 (회차 동안) ===")
    for series in sorted(counters):
        first, last = counters[series]
        delta = last - first
        if delta:
            print(f"  {delta:>12,.0f}  {series}")

    print()
    print("=== 게이지 최댓값 ===")
    for series in sorted(gauges):
        if gauges[series]:
            print(f"  {gauges[series]:>12,.0f}  {series}")

    print()
    print("증가량 0 인 계열은 생략했다 — 거절·오버플로가 안 보이면 0이라는 뜻이다.")


if __name__ == "__main__":
    main()
