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

LINE = re.compile(r"^(\d+) (\w+) ([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})? (.+)$")

# 카운터는 증가량, 게이지는 최댓값으로 본다.
COUNTERS = (
    "stomp_executor_rejected_total",
    "chat_badge_coalesced_total",
    "chat_badge_flushed_total",
    "chat_message_batch_buffered_total",
    "chat_message_batch_frames_total",
    "chat_message_batch_overflow_total",
    "chat_message_ack_direct_sent_total",
    "chat_message_ack_direct_failed_total",
    "chat_badge_direct_sent_total",
    "chat_badge_direct_skipped_total",
    "chat_badge_direct_failed_total",
    "chat_message_persistence_messages_total",
    "chat_message_persistence_retry_failures_total",
    "chat_message_persistence_dlq_transitions_total",
    "chat_room_activity_projection_rooms_total",
    "chat_room_activity_projection_score_mismatches_total",
    "executor_completed_tasks_total",
)

GAUGES = (
    "executor_queued_tasks",
    "executor_active_threads",
    "chat_badge_pending",
    "chat_message_batch_pending_rooms",
    "chat_room_activity_projection_dirty_backlog",
    "ws_active_sessions",
)

SUMMARIES = (
    "chat_badge_flush_seconds",
    "chat_message_persistence_handler_seconds",
    "chat_message_persistence_stage_seconds",
    "chat_message_persistence_batch_messages",
    "chat_message_persistence_batch_rooms",
    "chat_room_activity_projection_flush_seconds",
    "chat_room_activity_projection_members",
)


def parse(path: Path):
    counters = defaultdict(lambda: [None, None])  # series -> [first, last]
    gauges = defaultdict(float)
    timers = defaultdict(lambda: {
        "count": [None, None],
        "sum": [None, None],
        "max": 0.0,
    })

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

        summary_name = next((candidate for candidate in SUMMARIES if name.startswith(f"{candidate}_")), None)

        if summary_name and name in (
            f"{summary_name}_count",
            f"{summary_name}_sum",
            f"{summary_name}_max",
        ):
            part = name.removeprefix(f"{summary_name}_")
            timer = timers[f"{source} {summary_name}{labels or ''}"]

            if part == "max":
                timer[part] = max(timer[part], value)
            else:
                slot = timer[part]
                if slot[0] is None:
                    slot[0] = value
                slot[1] = value
        elif name in COUNTERS:
            slot = counters[series]
            if slot[0] is None:
                slot[0] = value
            slot[1] = value
        elif name in GAUGES:
            gauges[series] = max(gauges[series], value)

    return counters, gauges, timers


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("사용법: tools/summarize-metrics.py <샘플파일>")

    path = Path(sys.argv[1])
    if not path.exists():
        sys.exit(f"파일 없음: {path}")

    counters, gauges, timers = parse(path)

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
    print("=== 타이머·분포 요약 (회차 동안) ===")
    for series in sorted(timers):
        timer = timers[series]
        count_first, count_last = timer["count"]
        sum_first, sum_last = timer["sum"]

        if count_first is None or sum_first is None:
            continue

        count_delta = count_last - count_first
        sum_delta = sum_last - sum_first
        if count_delta:
            average = sum_delta / count_delta
            print(
                f"  count={count_delta:,.0f} total={sum_delta:.6f}s "
                f"avg={average:.6f}s sampled_max={timer['max']:.6f}s  {series}"
            )

    print()
    print("증가량 0 인 계열은 생략했다 — 거절·오버플로가 안 보이면 0이라는 뜻이다.")


if __name__ == "__main__":
    main()
