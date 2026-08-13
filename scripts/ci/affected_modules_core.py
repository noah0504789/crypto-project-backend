#!/usr/bin/env python3

import re
import subprocess
from collections import defaultdict, deque
from pathlib import Path


INCLUDED_PROJECT_PATTERN = re.compile(r"""'([^']+)'|"([^"]+)\"""")

PROJECT_DEPENDENCY_PATTERN = re.compile(
    r"""^\s*(\w+)\s+project\(['"](:[^'"]+)['"]\)"""
)

DOCKER_IMAGE_NAME_PATTERN = re.compile(
    r"""^\s*ext\.dockerImageName\s*=\s*['"]([^'"]+)['"]"""
)

ARCH_TEST_TASK = ":common:common-arch-test:test"

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

            # CI 영향도 계산은 production 의존성 기준으로만 본다.
            # testImplementation/testRuntimeOnly 등은 제외한다.
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


def is_test_change(file_path: str) -> bool:
    return "/src/test/" in file_path


def is_docker_relevant_change(file_path: str) -> bool:
    if is_test_change(file_path):
        return False

    return (
            "/src/main/" in file_path
            or file_path.endswith("build.gradle")
            or file_path.endswith("build.gradle.kts")
            or file_path.endswith("Dockerfile")
    )


def is_arch_test_project(project: str) -> bool:
    return project == ":common:common-arch-test"


def should_run_arch_test(files: list[str], affected_projects: set[str]) -> bool:
    if any(file_path == "settings.gradle" for file_path in files):
        return True

    if any(file_path.endswith("build.gradle") or file_path.endswith("build.gradle.kts") for file_path in files):
        return True

    if any(file_path.startswith("common/") for file_path in files):
        return True

    if any(project.startswith(":common:") for project in affected_projects):
        return True

    return False


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

    # aggregate project와 하위 project가 동시에 매칭될 수 있으므로
    # 가장 깊은 project directory를 선택한다.
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
        expanded_projects = expand_aggregate_project(
            changed_project,
            projects,
            aggregates
        )

        for expanded_project in expanded_projects:
            affected_projects.add(expanded_project)
            queue.append(expanded_project)

    while queue:
        current_project = queue.popleft()

        for dependent_project in reversed_graph.get(current_project, set()):
            expanded_dependents = expand_aggregate_project(
                dependent_project,
                projects,
                aggregates
            )

            for expanded_dependent in expanded_dependents:
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

    tasks: list[str] = []

    for project in sorted(projects):
        if is_arch_test_project(project):
            tasks.append(ARCH_TEST_TASK)
            continue

        tasks.append(f"{project}:{task_name}")

    if include_arch_test and ARCH_TEST_TASK not in tasks:
        tasks.append(ARCH_TEST_TASK)

    return tasks


def read_docker_image_name(project_directory: Path) -> str | None:
    build_file = project_directory / "build.gradle"

    if not build_file.is_file():
        return None

    for line in build_file.read_text().splitlines():
        match = DOCKER_IMAGE_NAME_PATTERN.match(line)

        if match:
            return match.group(1)

    return None


def to_docker_services(
        root: Path,
        projects: set[str],
        project_directories: dict[str, Path]
) -> list[str]:
    services: list[str] = []

    for project in sorted(projects):
        project_directory = project_directories[project]
        dockerfile = project_directory / "Dockerfile"

        if not dockerfile.is_file():
            continue

        docker_image_name = read_docker_image_name(project_directory)

        if not docker_image_name:
            continue

        service_path = project_directory.relative_to(root).as_posix()
        services.append(f"{service_path}={docker_image_name}")

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

        dockerfile = project_directory / "Dockerfile"

        if not dockerfile.is_file():
            continue

        docker_image_name = read_docker_image_name(project_directory)

        if not docker_image_name:
            continue

        service_path = project_directory.relative_to(root).as_posix()
        services.append(f"{service_path}={docker_image_name}")

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

    target_files = files

    if mode == "docker":
        # 테스트 파일만 바뀐 경우 Docker 이미지는 다시 굽지 않는다.
        # main source, resources, build.gradle, Dockerfile 변경만 Docker 영향도로 본다.
        target_files = [
            file_path
            for file_path in files
            if is_docker_relevant_change(file_path)
        ]

    affected_projects = calculate_affected_projects_from_files(
        root=root,
        files=target_files,
        projects=projects,
        aggregates=aggregates,
        reversed_graph=reversed_graph
    )

    if not affected_projects:
        return []

    effective_include_arch_test = include_arch_test or should_run_arch_test(
        files=files,
        affected_projects=affected_projects
    )

    if mode == "docker":
        return to_docker_services(
            root=root,
            projects=affected_projects,
            project_directories=projects
        )

    return to_gradle_tasks(
        projects=affected_projects,
        mode=mode,
        include_arch_test=effective_include_arch_test
    )


def find_root() -> Path:
    current = Path.cwd().resolve()

    while True:
        if (current / "settings.gradle").is_file():
            return current

        parent = current.parent

        if parent == current:
            break

        current = parent

    raise RuntimeError("Cannot locate repository root")
