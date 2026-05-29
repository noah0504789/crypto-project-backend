from pathlib import Path

import affected_modules as am


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n")


def create_fake_project(root: Path) -> None:
    write(
        root / "settings.gradle",
        """
        rootProject.name = 'fake-project'

        include 'common'
        include 'common:common-core'
        include 'common:common-redis'
        include 'common:common-test'

        include 'protobuf'

        include 'chat'
        include 'chat:chat-contract'
        include 'chat:chat-client'
        include 'chat:chat-domain'
        include 'chat:chat-application'
        include 'chat:chat-bootstrap'

        include 'websocket-gateway'
        include 'websocket-gateway:websocket-gateway-application'
        include 'websocket-gateway:websocket-gateway-adapter-out'
        include 'websocket-gateway:websocket-gateway-bootstrap'
        """,
        )

    write(
        root / "common/common-redis/build.gradle",
        """
        dependencies {
            api project(':common:common-core')
            testImplementation project(':common:common-test')
        }
        """,
        )

    write(
        root / "chat/chat-client/build.gradle",
        """
        dependencies {
            api project(':protobuf')
        }
        """,
        )

    write(
        root / "chat/chat-domain/build.gradle",
        """
        dependencies {
            api project(':common:common-core')
            api project(':chat:chat-contract')
        }
        """,
        )

    write(
        root / "chat/chat-application/build.gradle",
        """
        dependencies {
            api project(':chat:chat-domain')
            implementation project(':common:common-redis')
        }
        """,
        )

    write(
        root / "chat/chat-bootstrap/build.gradle",
        """
        dependencies {
            implementation project(':chat:chat-domain')
            implementation project(':chat:chat-application')
        }
        """,
        )

    write(
        root / "websocket-gateway/websocket-gateway-application/build.gradle",
        """
        dependencies {
            api project(':protobuf')
        }
        """,
        )

    write(
        root / "websocket-gateway/websocket-gateway-adapter-out/build.gradle",
        """
        dependencies {
            implementation project(':chat:chat-client')
            implementation project(':websocket-gateway:websocket-gateway-application')
        }
        """,
        )

    write(
        root / "websocket-gateway/websocket-gateway-bootstrap/build.gradle",
        """
        dependencies {
            implementation project(':websocket-gateway:websocket-gateway-application')
            implementation project(':websocket-gateway:websocket-gateway-adapter-out')
        }
        """,
        )

    write(root / "common/common-core/src/main/java/Dummy.java", "class Dummy {}")
    write(root / "protobuf/src/main/proto/chat.proto", "syntax = 'proto3';")
    write(root / "chat/chat-domain/src/main/java/Chat.java", "class Chat {}")
    write(root / "chat/build.gradle", "dependencies {}")
    write(root / "docs/README.md", "# docs")


def load_graph(root: Path):
    projects = am.read_projects(root)
    aggregates = am.find_aggregate_projects(projects)
    dependencies = am.read_dependencies(projects)
    reversed_graph = am.reverse_dependencies(dependencies)

    return projects, aggregates, dependencies, reversed_graph


def test_read_projects_from_settings_gradle(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)

    assert ":common:common-core" in projects
    assert ":common:common-redis" in projects
    assert ":protobuf" in projects
    assert ":chat:chat-domain" in projects
    assert ":websocket-gateway:websocket-gateway-bootstrap" in projects
    assert projects[":chat:chat-domain"] == tmp_path / "chat/chat-domain"


def test_find_aggregate_projects(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)
    aggregates = am.find_aggregate_projects(projects)

    assert ":common" in aggregates
    assert ":chat" in aggregates
    assert ":websocket-gateway" in aggregates

    assert ":protobuf" not in aggregates
    assert ":chat:chat-domain" not in aggregates


def test_read_dependencies_excludes_test_dependencies(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)
    dependencies = am.read_dependencies(projects)

    assert dependencies[":common:common-redis"] == {":common:common-core"}
    assert ":common:common-test" not in dependencies[":common:common-redis"]


def test_reverse_dependencies(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)
    dependencies = am.read_dependencies(projects)
    reversed_graph = am.reverse_dependencies(dependencies)

    assert ":chat:chat-domain" in reversed_graph[":common:common-core"]
    assert ":chat:chat-application" in reversed_graph[":chat:chat-domain"]
    assert ":chat:chat-bootstrap" in reversed_graph[":chat:chat-application"]


def test_find_project_for_file_chooses_deepest_module(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)

    project = am.find_project_for_file(
        tmp_path,
        projects,
        "chat/chat-domain/src/main/java/Chat.java",
    )

    assert project == ":chat:chat-domain"


def test_find_project_for_file_returns_none_for_non_project_file(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)

    project = am.find_project_for_file(
        tmp_path,
        projects,
        "docs/README.md",
    )

    assert project is None


def test_common_core_change_affects_dependents_transitively(tmp_path):
    create_fake_project(tmp_path)

    projects, aggregates, _, reversed_graph = load_graph(tmp_path)

    affected = am.find_affected_projects(
        changed_projects={":common:common-core"},
        projects=projects,
        aggregates=aggregates,
        reversed_graph=reversed_graph,
    )

    assert ":common:common-core" in affected
    assert ":common:common-redis" in affected
    assert ":chat:chat-domain" in affected
    assert ":chat:chat-application" in affected
    assert ":chat:chat-bootstrap" in affected


def test_protobuf_change_affects_protobuf_dependents_transitively(tmp_path):
    create_fake_project(tmp_path)

    projects, aggregates, _, reversed_graph = load_graph(tmp_path)

    affected = am.find_affected_projects(
        changed_projects={":protobuf"},
        projects=projects,
        aggregates=aggregates,
        reversed_graph=reversed_graph,
    )

    assert ":protobuf" in affected
    assert ":chat:chat-client" in affected
    assert ":websocket-gateway:websocket-gateway-application" in affected
    assert ":websocket-gateway:websocket-gateway-adapter-out" in affected
    assert ":websocket-gateway:websocket-gateway-bootstrap" in affected

    assert ":chat:chat-domain" not in affected


def test_aggregate_project_change_expands_to_child_modules(tmp_path):
    create_fake_project(tmp_path)

    projects, aggregates, _, reversed_graph = load_graph(tmp_path)

    affected = am.find_affected_projects(
        changed_projects={":chat"},
        projects=projects,
        aggregates=aggregates,
        reversed_graph=reversed_graph,
    )

    assert ":chat:chat-contract" in affected
    assert ":chat:chat-client" in affected
    assert ":chat:chat-domain" in affected
    assert ":chat:chat-application" in affected
    assert ":chat:chat-bootstrap" in affected


def test_global_change_detection():
    assert am.is_global_change("settings.gradle")
    assert am.is_global_change("build.gradle")
    assert am.is_global_change("gradlew")
    assert am.is_global_change("gradlew.bat")
    assert am.is_global_change("build-logic/src/main/java/Test.java")
    assert am.is_global_change(".github/workflows/ci.yml")
    assert am.is_global_change("gradle/libs.versions.toml")

    assert not am.is_global_change("common/common-core/src/main/java/A.java")
    assert not am.is_global_change("protobuf/src/main/proto/chat.proto")
    assert not am.is_global_change("chat/chat-domain/src/main/java/A.java")
    assert not am.is_global_change("docs/README.md")


def test_to_gradle_tasks_with_arch_test():
    tasks = am.to_gradle_tasks(
        {":chat:chat-domain", ":chat:chat-application"},
        mode="build",
        include_arch_test=True,
    )

    assert tasks == [
        ":chat:chat-application:build",
        ":chat:chat-domain:build",
        ":common:common-arch-test:test",
    ]


def test_to_gradle_tasks_assemble_mode():
    tasks = am.to_gradle_tasks(
        {":chat:chat-domain"},
        mode="assemble",
        include_arch_test=False,
    )

    assert tasks == [":chat:chat-domain:assemble"]


def test_calculate_affected_tasks_returns_fallback_for_global_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_affected_tasks(
        root=tmp_path,
        files=["settings.gradle"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == ["serviceCi"]


def test_calculate_affected_tasks_returns_empty_for_docs_only_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_affected_tasks(
        root=tmp_path,
        files=["docs/README.md"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == []


def test_calculate_affected_tasks_for_chat_domain_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_affected_tasks(
        root=tmp_path,
        files=["chat/chat-domain/src/main/java/Chat.java"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == [
        ":chat:chat-application:build",
        ":chat:chat-bootstrap:build",
        ":chat:chat-domain:build",
        ":common:common-arch-test:test",
    ]


def test_calculate_affected_tasks_for_protobuf_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_affected_tasks(
        root=tmp_path,
        files=["protobuf/src/main/proto/chat.proto"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == [
        ":chat:chat-client:build",
        ":protobuf:build",
        ":websocket-gateway:websocket-gateway-adapter-out:build",
        ":websocket-gateway:websocket-gateway-application:build",
        ":websocket-gateway:websocket-gateway-bootstrap:build",
        ":common:common-arch-test:test",
    ]