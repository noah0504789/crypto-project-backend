#!/usr/bin/env python3
"""build.gradle 의 project(...) 의존 선언 순서를 검사한다(docs/CODE_STYLE.md §6).

같은 configuration(api/implementation/testImplementation ...) 의 연속 선언 안에서
common -> 다른 서비스 -> 자기 서비스 순인지 본다. configuration 이 다르면 의미가 달라
(api 는 전이 노출) 섞어 정렬하지 않는다.

  python3 scripts/quality/dependency_order.py           # 검사만(위반 시 exit 1)
  python3 scripts/quality/dependency_order.py --apply   # 정렬 적용
"""

import re, sys, pathlib

DEP = re.compile(r"^\s*(\w+)\s+project\('(:[^']+)'\)")

def rank(dep_path, own):
    svc = dep_path.split(':')[1]
    return 0 if svc == 'common' else (2 if svc == own else 1)

def blocks_of(lines, own):
    """같은 configuration 의 연속 project(...) 선언만 한 블록으로 본다."""
    out, cur, cur_cfg = [], [], None
    for i, line in enumerate(lines):
        m = DEP.match(line)
        if m and (cur_cfg is None or m.group(1) == cur_cfg) and (not cur or i == cur[-1] + 1):
            cur.append(i); cur_cfg = m.group(1)
        else:
            if len(cur) > 1:
                out.append(cur)
            cur, cur_cfg = ([i], m.group(1)) if m else ([], None)
    if len(cur) > 1:
        out.append(cur)
    return out

def process(path, apply):
    own = str(path).split('/')[0]
    lines = path.read_text().splitlines(keepends=True)
    violated = False
    for block in blocks_of(lines, own):
        keys = [rank(DEP.match(lines[i]).group(2), own) for i in block]
        if keys != sorted(keys):
            violated = True
            ordered = sorted(range(len(block)), key=lambda k: (keys[k], k))
            picked = [lines[block[k]] for k in ordered]
            for pos, line in zip(block, picked):
                lines[pos] = line
    if violated and apply:
        path.write_text(''.join(lines))
    return violated

apply = '--apply' in sys.argv
bad = []
for f in pathlib.Path('.').rglob('build.gradle'):
    sf = str(f)
    if 'build/' in sf or sf.startswith('build-logic'):
        continue
    if process(f, apply):
        bad.append(sf)
print(("정렬함: " if apply else "위반: ") + str(len(bad)))
for b in bad:
    print(" -", b)

if bad and not apply:
    print()
    print("의존 선언 순서를 common -> 다른 서비스 -> 자기 서비스 로 맞춘다.")
    print("자동 정렬: python3 scripts/quality/dependency_order.py --apply")
    sys.exit(1)
