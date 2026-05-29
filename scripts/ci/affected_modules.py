#!/usr/bin/env python3

import argparse
import re
import subprocess
from pathlib import Path
from collections import defaultdict, deque

INCLUDE_PATTERN = re.compile(r"""include\s+(.+)""")
INCLUDED_PROJECT_PATTERN = re.compile(r"""'([^']+)'|"([^"]+)\"""")
PROJECT_DEP_PATTERN = re.compile(r"""^\s*\w+\s+project\(['"](:[^'"]+)['"]\)""")

GLOBAL_PATH_PREFIXES = (
    "gradle/",
    "build-logic/",
    ".github/workflows/",
)

GLOBAL_FILES = {
    "settings.gradle",
    "build.gradle",
    "gradlew",
    "gradlew.bat",
}

def run(command):
    return subprocess.check_output(command, text=True).strip()

def read_projects(root: Path):
    settings = root / "settings.gradle"
    projects = {}

    for line in settings.read_text().splitlines():
        line = line.strip()
        if not line.startswith("include "):
            continue

        for match in INCLUDED_PROJECT_PATTERN.finditer(line):
            included = match.group(1) or match.group(2)
            path = ":" + included
            directory = root / included.replace(":", "/")
            projects[path] = directory

    return projects

def find_aggregate_projects(projects):
    aggregates = set()
    paths = set(projects.keys())

    for path in paths:
        prefix = path + ":"
        if any(candidate.startswith(prefix) for candidate in paths):
            aggregates.add(path)

    return aggregates

def read_dependencies(projects):
    dependencies = defaultdict(set)

    for project_path, directory in projects.items():
        build_file = directory / "build.gradle"
        if not build_file.is_file():
            continue

        for line in build_file.read_text().splitlines():
            match = PROJECT_DEP_PATTERN.match(line)
            if match:
                dependencies[project_path].add(match.group(1))

    return dependencies

def reverse_dependencies(dependencies):
    reversed_graph = defaultdict(set)

    for source, targets in dependencies.items():
        for target in targets:
            reversed_graph[target].add(source)

    return reversed_graph

def changed_files(base, head):
    try:
        output = run(["git", "diff", "--name-only", f"{base}...{head}"])
    except subprocess.CalledProcessError:
        output = run(["git", "diff", "--name-only", f"{base}..{head}"])

    if not output:
        return []

    return [line.strip() for line in output.splitlines() if line.strip()]

def is_global_change(file_path):
    if file_path in GLOBAL_FILES:
        return True

    return file_path.startswith(GLOBAL_PATH_PREFIXES)

def find_project_for_file(root, projects, file_path):
    absolute = (root / file_path).resolve()

    candidates = []
    for project_path, directory in projects.items():
        try:
            absolute.relative_to(directory.resolve())
            candidates.append((project_path, len(directory.parts)))
        except ValueError:
            pass

    if not candidates:
        return None

    candidates.sort(key=lambda item: item[1], reverse=True)
    return candidates[0][0]

def expand_aggregate(project, projects, aggregates):
    if project not in aggregates:
        return {project}

    prefix = project + ":"
    return {
        candidate
        for candidate in projects
        if candidate.startswith(prefix) and candidate not in aggregates
    }

def find_affected_projects(changed_projects, projects, aggregates, reversed_graph):
    affected = set()
    queue = deque()

    for project in changed_projects:
        for expanded in expand_aggregate(project, projects, aggregates):
            affected.add(expanded)
            queue.append(expanded)

    while queue:
        current = queue.popleft()

        for dependent in reversed_graph.get(current, set()):
            expanded_dependents = expand_aggregate(dependent, projects, aggregates)

            for expanded in expanded_dependents:
                if expanded not in affected:
                    affected.add(expanded)
                    queue.append(expanded)

    return affected

def to_tasks(projects, mode, include_arch_test):
    task_name = "assemble" if mode == "assemble" else "build"
    tasks = [f"{project}:{task_name}" for project in sorted(projects)]

    if include_arch_test:
        tasks.append(":common:common-arch-test:test")

    return tasks

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--mode", choices=["assemble", "build"], default="build")
    parser.add_argument("--include-arch-test", action="store_true")
    parser.add_argument("--fallback-task", default="serviceCi")
    args = parser.parse_args()

    root = Path.cwd()
    projects = read_projects(root)
    aggregates = find_aggregate_projects(projects)
    dependencies = read_dependencies(projects)
    reversed_graph = reverse_dependencies(dependencies)

    files = changed_files(args.base, args.head)

    if any(is_global_change(file) for file in files):
        print(args.fallback_task)
        return

    changed_projects = set()

    for file in files:
        project = find_project_for_file(root, projects, file)
        if project:
            changed_projects.add(project)

    affected = find_affected_projects(
        changed_projects,
        projects,
        aggregates,
        reversed_graph
    )

    affected = {
        project
        for project in affected
        if project in projects and project not in aggregates
    }

    if not affected:
        print("")
        return

    tasks = to_tasks(
        affected,
        args.mode,
        include_arch_test=args.include_arch_test
    )

    print(" ".join(tasks))

if __name__ == "__main__":
    main()