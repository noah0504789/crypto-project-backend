#!/usr/bin/env python3
import argparse
import json
import re
from pathlib import Path


METRIC_PATTERN = re.compile(r"^(?P<name>[a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(?P<labels>.*)\})? (?P<value>[-+0-9.eE]+)$")
LABEL_PATTERN = re.compile(r'([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"])*)"')


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="ChatMessage 쓰기 부하테스트 결과 요약")
    parser.add_argument("--before-mongo", type=Path, required=True)
    parser.add_argument("--after-mongo", type=Path, required=True)
    parser.add_argument("--before-metrics", type=Path, required=True)
    parser.add_argument("--after-metrics", type=Path, required=True)
    parser.add_argument("--expected", type=int, required=True)
    parser.add_argument("--persisted", type=int, required=True)
    parser.add_argument("--members", type=int, required=True)
    parser.add_argument("--publish-seconds", type=float, required=True)
    parser.add_argument("--drain-seconds", type=float, required=True)
    return parser.parse_args()


def metric_values(path: Path) -> dict[tuple[str, tuple[tuple[str, str], ...]], float]:
    values = {}
    for line in path.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        match = METRIC_PATTERN.match(line)
        if not match:
            continue
        labels = tuple(sorted(LABEL_PATTERN.findall(match.group("labels") or "")))
        values[(match.group("name"), labels)] = float(match.group("value"))
    return values


def metric_delta(before: dict, after: dict, name: str, labels: dict[str, str] | None = None) -> float | None:
    expected_labels = labels or {}
    matches = []
    for key, after_value in after.items():
        metric_name, metric_labels = key
        label_map = dict(metric_labels)
        if metric_name == name and all(label_map.get(k) == v for k, v in expected_labels.items()):
            matches.append(after_value - before.get(key, 0.0))
    return sum(matches) if matches else None


def delta(before: dict, after: dict, section: str, key: str) -> int:
    return int(after[section][key] - before[section][key])


def display(value: float | None, decimals: int = 0) -> str:
    if value is None:
        return "미노출"
    return f"{value:,.{decimals}f}"


def main() -> None:
    args = parse_args()
    mongo_before = json.loads(args.before_mongo.read_text())
    mongo_after = json.loads(args.after_mongo.read_text())
    metrics_before = metric_values(args.before_metrics)
    metrics_after = metric_values(args.after_metrics)

    inserted = delta(mongo_before, mongo_after, "opcounters", "insert")
    updated = delta(mongo_before, mongo_after, "opcounters", "update")
    deleted = delta(mongo_before, mongo_after, "opcounters", "delete")
    committed = delta(mongo_before, mongo_after, "transactions", "committed")
    aborted = delta(mongo_before, mongo_after, "transactions", "aborted")
    persistence_writes = inserted + updated
    per_message = persistence_writes / args.persisted if args.persisted else 0

    new_messages = metric_delta(
        metrics_before,
        metrics_after,
        "chat_message_persistence_messages_total",
        {"result": "new"},
    )
    batch_count = metric_delta(metrics_before, metrics_after, "chat_message_persistence_batch_messages_count")
    batch_sum = metric_delta(metrics_before, metrics_after, "chat_message_persistence_batch_messages_sum")
    average_batch = batch_sum / batch_count if batch_sum is not None and batch_count else None
    retries = metric_delta(metrics_before, metrics_after, "chat_message_persistence_retry_failures_total")

    print("# 측정 요약")
    print()
    print("| 항목 | 값 |")
    print("|---|---:|")
    print(f"| 기대 / 저장 메시지 | {args.expected:,} / {args.persisted:,} |")
    print(f"| 방 멤버 | {args.members:,} |")
    print(f"| 발행 / 전체 drain 시간 | {args.publish_seconds:.3f}s / {args.drain_seconds:.3f}s |")
    print(f"| Mongo insert op | {inserted:,} |")
    print(f"| Mongo update op | {updated:,} |")
    print(f"| Mongo delete op (측정 경로 외 참고값) | {deleted:,} |")
    print(f"| 메시지당 persistence write op | {per_message:,.3f} |")
    print(f"| Mongo transaction commit / abort | {committed:,} / {aborted:,} |")
    print(f"| 앱 계측 신규 메시지 | {display(new_messages)} |")
    print(f"| 앱 계측 batch 수 / 평균 크기 | {display(batch_count)} / {display(average_batch, 2)} |")
    print(f"| 앱 계측 retryable failure | {display(retries)} |")
    print()
    print("> Mongo op 값은 primary `serverStatus.opcounters`의 회차 전후 차이다. 메시지 영속 경로의 "
          "비교값은 insert + update이며, 이 경로에서 발생하지 않는 delete는 전역 background activity를 "
          "확인하는 참고값으로만 표시한다. bulkWrite 안의 update model도 각각 집계된다.")


if __name__ == "__main__":
    main()
