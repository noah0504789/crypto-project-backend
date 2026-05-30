#!/usr/bin/env python3

import argparse
import re
import subprocess
from collections import defaultdict, deque
from pathlib import Path


INCLUDED_PROJECT_PATTERN = re.compile(r"""'([^']+)'|"([^"]+)\"""")
PROJECT_DEPENDENCY_PATTERN = re.compile(
    r"""^\s*(\w+)\s+project\(['"](:[^'"]+)['"]\)"""
)
DOCKER_IMAGE_NAME_PATTERN = re.compile(
    r"""dockerImageName\s*=\s*['"]([^'"]+)['"]"""
)

GLOBAL_PATH_PREFIXES = (
    "gradle/",
    "build-logic/",
    ".github/workflows/",
    "scripts/ci/",
)

GLOBAL_FILES = {
    "settings.gradle",
    "build.gradle",
    "gradlew",
    "gradlew.bat",
}


def run(command: list[str]) -> str:
    return subprocess.check_output(command, text=True).strip()


def read_projects(root: Path) -> dict[str, Path]:
    projects: dict[str, Path] = {}
    settings_file = root / "settings.gradle"

    for line in settings_file.read_text().splitlines():
        line = line.strip()

        if not line.startswith("include "):
            continue

        for match in INCLUDED_PROJECT_PATTERN.finditer(line):
            included = match.group(1) or match.group(2)

            project_path = ":" + included
            project_directory = root / included.replace(":", "/")

            projects[project_path] = project_directory

    return projects


def find_aggregate_projects(projects: dict[str, Path]) -> set[str]:
    aggregates: set[str] = set()
    project_paths = set(projects.keys())

    for project_path in project_paths:
        child_prefix = project_path + ":"

        if any(candidate.startswith(child_prefix) for candidate in project_paths):
            aggregates.add(project_path)

    return aggregates


def read_dependencies(projects: dict[str, Path]) -> dict[str, set[str]]:
    dependencies: dict[str, set[str]] = defaultdict(set)

    for project_path, project_directory in projects.items():
        build_file = project_directory / "build.gradle"

        if not build_file.is_file():
            continue

        for line in build_file.read_text().splitlines():
            match = PROJECT_DEPENDENCY_PATTERN.match(line)

            if not match:
                continue

            configuration = match.group(1)
            target_project = match.group(2)

            if configuration.startswith("test"):
                continue

            dependencies[project_path].add(target_project)

    return dependencies


def reverse_dependencies(dependencies: dict[str, set[str]]) -> dict[str, set[str]]:
    reversed_graph: dict[str, set[str]] = defaultdict(set)

    for source_project, target_projects in dependencies.items():
        for target_project in target_projects:
            reversed_graph[target_project].add(source_project)

    return reversed_graph


def changed_files(base: str, head: str) -> list[str]:
    try:
        output = run(["git", "diff", "--name-only", f"{base}...{head}"])
    except subprocess.CalledProcessError:
        output = run(["git", "diff", "--name-only", f"{base}..{head}"])

    if not output:
        return []

    return [line.strip() for line in output.splitlines() if line.strip()]


def is_global_change(file_path: str) -> bool:
    if file_path in GLOBAL_FILES:
        return True

    return file_path.startswith(GLOBAL_PATH_PREFIXES)


def find_project_for_file(
        root: Path,
        projects: dict[str, Path],
        file_path: str
) -> str | None:
    absolute_file_path = (root / file_path).resolve()
    candidates: list[tuple[str, int]] = []

    for project_path, project_directory in projects.items():
        try:
            absolute_file_path.relative_to(project_directory.resolve())
            candidates.append((project_path, len(project_directory.parts)))
        except ValueError:
            pass

    if not candidates:
        return None

    candidates.sort(key=lambda item: item[1], reverse=True)
    return candidates[0][0]


def expand_aggregate_project(
        project_path: str,
        projects: dict[str, Path],
        aggregates: set[str]
) -> set[str]:
    if project_path not in aggregates:
        return {project_path}

    child_prefix = project_path + ":"

    return {
        candidate
        for candidate in projects
        if candidate.startswith(child_prefix) and candidate not in aggregates
    }


def find_affected_projects(
        changed_projects: set[str],
        projects: dict[str, Path],
        aggregates: set[str],
        reversed_graph: dict[str, set[str]]
) -> set[str]:
    affected_projects: set[str] = set()
    queue: deque[str] = deque()

    for changed_project in changed_projects:
        for expanded_project in expand_aggregate_project(changed_project, projects, aggregates):
            affected_projects.add(expanded_project)
            queue.append(expanded_project)

    while queue:
        current_project = queue.popleft()

        for dependent_project in reversed_graph.get(current_project, set()):
            for expanded_dependent in expand_aggregate_project(dependent_project, projects, aggregates):
                if expanded_dependent not in affected_projects:
                    affected_projects.add(expanded_dependent)
                    queue.append(expanded_dependent)

    return affected_projects


def to_gradle_tasks(
        projects: set[str],
        mode: str,
        include_arch_test: bool
) -> list[str]:
    task_name = "assemble" if mode == "assemble" else "build"

    tasks = [
        f"{project}:{task_name}"
        for project in sorted(projects)
    ]

    if include_arch_test:
        tasks.append(":common:common-arch-test:test")

    return tasks


def read_docker_image_name(project_directory: Path) -> str | None:
    build_file = project_directory / "build.gradle"

    if not build_file.is_file():
        return None

    for line in build_file.read_text().splitlines():
        match = DOCKER_IMAGE_NAME_PATTERN.search(line)

        if match:
            return match.group(1)

    return None


def docker_service_entry(root: Path, project_directory: Path) -> str | None:
    dockerfile = project_directory / "Dockerfile"

    if not dockerfile.is_file():
        return None

    image_name = read_docker_image_name(project_directory)

    if not image_name:
        raise ValueError(
            f"dockerImageName is required in {project_directory / 'build.gradle'}"
        )

    service_path = project_directory.relative_to(root).as_posix()

    return f"{service_path}={image_name}"


def to_docker_services(
        root: Path,
        projects: set[str],
        project_directories: dict[str, Path]
) -> list[str]:
    services: list[str] = []

    for project in sorted(projects):
        entry = docker_service_entry(root, project_directories[project])

        if entry:
            services.append(entry)

    return services


def all_docker_services(
        root: Path,
        projects: dict[str, Path],
        aggregates: set[str]
) -> list[str]:
    services: list[str] = []

    for project, project_directory in sorted(projects.items()):
        if project in aggregates:
            continue

        entry = docker_service_entry(root, project_directory)

        if entry:
            services.append(entry)

    return services


def calculate_affected_projects_from_files(
        root: Path,
        files: list[str],
        projects: dict[str, Path],
        aggregates: set[str],
        reversed_graph: dict[str, set[str]]
) -> set[str]:
    changed_projects: set[str] = set()

    for file_path in files:
        project = find_project_for_file(root, projects, file_path)

        if project:
            changed_projects.add(project)

    affected_projects = find_affected_projects(
        changed_projects,
        projects,
        aggregates,
        reversed_graph
    )

    return {
        project
        for project in affected_projects
        if project in projects and project not in aggregates
    }


def calculate_output(
        root: Path,
        files: list[str],
        mode: str,
        include_arch_test: bool,
        fallback_task: str
) -> list[str]:
    projects = read_projects(root)
    aggregates = find_aggregate_projects(projects)
    dependencies = read_dependencies(projects)
    reversed_graph = reverse_dependencies(dependencies)

    if any(is_global_change(file_path) for file_path in files):
        if mode == "docker":
            return all_docker_services(root, projects, aggregates)

        return [fallback_task]

    affected_projects = calculate_affected_projects_from_files(
        root=root,
        files=files,
        projects=projects,
        aggregates=aggregates,
        reversed_graph=reversed_graph
    )

    if not affected_projects:
        return []

    if mode == "docker":
        return to_docker_services(
            root=root,
            projects=affected_projects,
            project_directories=projects
        )

    return to_gradle_tasks(
        affected_projects,
        mode=mode,
        include_arch_test=include_arch_test
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument(
        "--mode",
        choices=["assemble", "build", "docker"],
        default="build"
    )
    parser.add_argument("--include-arch-test", action="store_true")
    parser.add_argument("--fallback-task", default="serviceCi")

    args = parser.parse_args()

    root = Path.cwd()
    files = changed_files(args.base, args.head)

    output = calculate_output(
        root=root,
        files=files,
        mode=args.mode,
        include_arch_test=args.include_arch_test,
        fallback_task=args.fallback_task
    )

    print(" ".join(output))


if __name__ == "__main__":
    main()