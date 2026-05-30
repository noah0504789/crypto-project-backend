from pathlib import Path

import pytest

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
        include 'common:common-actuator-core'
        include 'common:common-actuator-webmvc'

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

        include 'spring-cloud-eureka-server'
        include 'spring-cloud-api-gateway'
        include 'outbox-poller'
        """,
        )

    gradle_files = {
        "common/common-redis/build.gradle": """
            dependencies {
                api project(':common:common-core')
                testImplementation project(':common:common-test')
            }
        """,
        "common/common-actuator-core/build.gradle": """
            dependencies {}
        """,
        "common/common-actuator-webmvc/build.gradle": """
            dependencies {
                api project(':common:common-actuator-core')
            }
        """,
        "chat/chat-client/build.gradle": """
            dependencies {
                api project(':protobuf')
            }
        """,
        "chat/chat-domain/build.gradle": """
            dependencies {
                api project(':common:common-core')
                api project(':chat:chat-contract')
            }
        """,
        "chat/chat-application/build.gradle": """
            dependencies {
                api project(':chat:chat-domain')
                implementation project(':common:common-redis')
            }
        """,
        "chat/chat-bootstrap/build.gradle": """
            ext.dockerImageName = "crypto-chat-service"

            dependencies {
                implementation project(':chat:chat-domain')
                implementation project(':chat:chat-application')
                implementation project(':common:common-actuator-webmvc')
            }
        """,
        "websocket-gateway/websocket-gateway-application/build.gradle": """
            dependencies {
                api project(':protobuf')
            }
        """,
        "websocket-gateway/websocket-gateway-adapter-out/build.gradle": """
            dependencies {
                implementation project(':chat:chat-client')
                implementation project(':websocket-gateway:websocket-gateway-application')
            }
        """,
        "websocket-gateway/websocket-gateway-bootstrap/build.gradle": """
            ext.dockerImageName = "crypto-websocket-gateway"

            dependencies {
                implementation project(':websocket-gateway:websocket-gateway-application')
                implementation project(':websocket-gateway:websocket-gateway-adapter-out')
                implementation project(':common:common-actuator-webmvc')
            }
        """,
        "spring-cloud-eureka-server/build.gradle": """
            ext.dockerImageName = "crypto-spring-cloud-eureka-server"

            dependencies {
                implementation project(':common:common-actuator-webmvc')
            }
        """,
        "spring-cloud-api-gateway/build.gradle": """
            ext.dockerImageName = "crypto-spring-cloud-api-gateway"

            dependencies {
                implementation project(':common:common-actuator-webmvc')
            }
        """,
        "outbox-poller/build.gradle": """
            ext.dockerImageName = "crypto-outbox-poller"

            dependencies {
                implementation project(':common:common-actuator-webmvc')
            }
        """,
        "chat/build.gradle": "dependencies {}",
    }

    for file_path, content in gradle_files.items():
        write(root / file_path, content)

    files = {
        "common/common-core/src/main/java/Dummy.java": "class Dummy {}",
        "common/common-actuator-core/src/main/java/DeploymentReadiness.java": "class DeploymentReadiness {}",
        "common/common-actuator-webmvc/src/main/java/DeploymentReadinessController.java": "class DeploymentReadinessController {}",
        "protobuf/src/main/proto/chat.proto": "syntax = 'proto3';",
        "chat/chat-domain/src/main/java/Chat.java": "class Chat {}",
        "docs/README.md": "# docs",
        "chat/chat-bootstrap/Dockerfile": "FROM eclipse-temurin:17-jre",
        "websocket-gateway/websocket-gateway-bootstrap/Dockerfile": "FROM eclipse-temurin:17-jre",
        "spring-cloud-eureka-server/Dockerfile": "FROM eclipse-temurin:17-jre",
        "spring-cloud-api-gateway/Dockerfile": "FROM eclipse-temurin:17-jre",
        "outbox-poller/Dockerfile": "FROM eclipse-temurin:17-jre",
    }

    for file_path, content in files.items():
        write(root / file_path, content)


def load_graph(root: Path):
    projects = am.read_projects(root)
    aggregates = am.find_aggregate_projects(projects)
    dependencies = am.read_dependencies(projects)
    reversed_graph = am.reverse_dependencies(dependencies)

    return projects, aggregates, dependencies, reversed_graph


def test_global_change_detection():
    global_changes = [
        "settings.gradle",
        "build.gradle",
        "gradlew",
        "gradlew.bat",
        "build-logic/src/main/java/Test.java",
        ".github/workflows/ci.yml",
        "gradle/libs.versions.toml",
        "scripts/ci/affected_modules.py",
        "scripts/ci/test_affected_modules.py",
    ]

    non_global_changes = [
        "common/common-core/src/main/java/A.java",
        "protobuf/src/main/proto/chat.proto",
        "chat/chat-domain/src/main/java/A.java",
        "docs/README.md",
    ]

    for file_path in global_changes:
        assert am.is_global_change(file_path)

    for file_path in non_global_changes:
        assert not am.is_global_change(file_path)


def test_read_projects_from_settings_gradle(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)

    assert ":common:common-core" in projects
    assert ":common:common-redis" in projects
    assert ":protobuf" in projects
    assert ":chat:chat-domain" in projects
    assert ":websocket-gateway:websocket-gateway-bootstrap" in projects
    assert ":spring-cloud-eureka-server" in projects
    assert ":outbox-poller" in projects
    assert projects[":chat:chat-domain"] == tmp_path / "chat/chat-domain"


def test_find_aggregate_projects(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)
    aggregates = am.find_aggregate_projects(projects)

    assert ":common" in aggregates
    assert ":chat" in aggregates
    assert ":websocket-gateway" in aggregates
    assert ":protobuf" not in aggregates
    assert ":spring-cloud-eureka-server" not in aggregates
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


def test_common_actuator_core_change_affects_webmvc_and_execution_modules(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
        root=tmp_path,
        files=["common/common-actuator-core/src/main/java/DeploymentReadiness.java"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == [
        ":chat:chat-bootstrap:build",
        ":common:common-actuator-core:build",
        ":common:common-actuator-webmvc:build",
        ":outbox-poller:build",
        ":spring-cloud-api-gateway:build",
        ":spring-cloud-eureka-server:build",
        ":websocket-gateway:websocket-gateway-bootstrap:build",
        ":common:common-arch-test:test",
    ]


def test_common_actuator_webmvc_change_affects_execution_modules(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
        root=tmp_path,
        files=["common/common-actuator-webmvc/src/main/java/DeploymentReadinessController.java"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == [
        ":chat:chat-bootstrap:build",
        ":common:common-actuator-webmvc:build",
        ":outbox-poller:build",
        ":spring-cloud-api-gateway:build",
        ":spring-cloud-eureka-server:build",
        ":websocket-gateway:websocket-gateway-bootstrap:build",
        ":common:common-arch-test:test",
    ]


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
    global_changes = [
        "settings.gradle",
        "build.gradle",
        "gradlew",
        "gradlew.bat",
        "build-logic/src/main/java/Test.java",
        ".github/workflows/ci.yml",
        "gradle/libs.versions.toml",
    ]

    non_global_changes = [
        "common/common-core/src/main/java/A.java",
        "protobuf/src/main/proto/chat.proto",
        "chat/chat-domain/src/main/java/A.java",
        "docs/README.md",
    ]

    for file_path in global_changes:
        assert am.is_global_change(file_path)

    for file_path in non_global_changes:
        assert not am.is_global_change(file_path)


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


def test_read_docker_image_name(tmp_path):
    create_fake_project(tmp_path)

    image_name = am.read_docker_image_name(
        tmp_path / "chat/chat-bootstrap"
    )

    assert image_name == "crypto-chat-service"


def test_docker_service_entry_requires_docker_image_name(tmp_path):
    create_fake_project(tmp_path)

    write(
        tmp_path / "broken-service/build.gradle",
        "dependencies {}",
        )
    write(
        tmp_path / "broken-service/Dockerfile",
        "FROM eclipse-temurin:17-jre",
        )

    with pytest.raises(ValueError, match="dockerImageName is required"):
        am.docker_service_entry(tmp_path, tmp_path / "broken-service")


def test_to_docker_services_returns_only_projects_with_dockerfile_and_image_name(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)

    services = am.to_docker_services(
        root=tmp_path,
        projects={
            ":chat:chat-domain",
            ":chat:chat-application",
            ":chat:chat-bootstrap",
            ":websocket-gateway:websocket-gateway-bootstrap",
        },
        project_directories=projects,
    )

    assert services == [
        "chat/chat-bootstrap=crypto-chat-service",
        "websocket-gateway/websocket-gateway-bootstrap=crypto-websocket-gateway",
    ]


def test_all_docker_services_returns_all_non_aggregate_projects_with_dockerfile(tmp_path):
    create_fake_project(tmp_path)

    projects = am.read_projects(tmp_path)
    aggregates = am.find_aggregate_projects(projects)

    services = am.all_docker_services(
        root=tmp_path,
        projects=projects,
        aggregates=aggregates,
    )

    assert services == [
        "chat/chat-bootstrap=crypto-chat-service",
        "outbox-poller=crypto-outbox-poller",
        "spring-cloud-api-gateway=crypto-spring-cloud-api-gateway",
        "spring-cloud-eureka-server=crypto-spring-cloud-eureka-server",
        "websocket-gateway/websocket-gateway-bootstrap=crypto-websocket-gateway",
    ]


def test_calculate_output_returns_fallback_for_global_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
        root=tmp_path,
        files=["settings.gradle"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == ["serviceCi"]


def test_calculate_output_returns_empty_for_docs_only_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
        root=tmp_path,
        files=["docs/README.md"],
        mode="build",
        include_arch_test=True,
        fallback_task="serviceCi",
    )

    assert tasks == []


def test_calculate_output_for_chat_domain_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
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


def test_calculate_output_for_protobuf_change(tmp_path):
    create_fake_project(tmp_path)

    tasks = am.calculate_output(
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


def test_calculate_output_docker_for_chat_domain_change(tmp_path):
    create_fake_project(tmp_path)

    services = am.calculate_output(
        root=tmp_path,
        files=["chat/chat-domain/src/main/java/Chat.java"],
        mode="docker",
        include_arch_test=False,
        fallback_task="serviceCi",
    )

    assert services == ["chat/chat-bootstrap=crypto-chat-service"]


def test_calculate_output_docker_for_protobuf_change(tmp_path):
    create_fake_project(tmp_path)

    services = am.calculate_output(
        root=tmp_path,
        files=["protobuf/src/main/proto/chat.proto"],
        mode="docker",
        include_arch_test=False,
        fallback_task="serviceCi",
    )

    assert services == [
        "websocket-gateway/websocket-gateway-bootstrap=crypto-websocket-gateway"
    ]


def test_calculate_output_docker_for_single_execution_module_change(tmp_path):
    create_fake_project(tmp_path)

    services = am.calculate_output(
        root=tmp_path,
        files=["outbox-poller/src/main/java/Poller.java"],
        mode="docker",
        include_arch_test=False,
        fallback_task="serviceCi",
    )

    assert services == ["outbox-poller=crypto-outbox-poller"]


def test_calculate_output_docker_for_global_change_returns_all_docker_services(tmp_path):
    create_fake_project(tmp_path)

    services = am.calculate_output(
        root=tmp_path,
        files=["settings.gradle"],
        mode="docker",
        include_arch_test=False,
        fallback_task="serviceCi",
    )

    assert services == [
        "chat/chat-bootstrap=crypto-chat-service",
        "outbox-poller=crypto-outbox-poller",
        "spring-cloud-api-gateway=crypto-spring-cloud-api-gateway",
        "spring-cloud-eureka-server=crypto-spring-cloud-eureka-server",
        "websocket-gateway/websocket-gateway-bootstrap=crypto-websocket-gateway",
    ]


def test_calculate_output_docker_returns_empty_for_docs_only_change(tmp_path):
    create_fake_project(tmp_path)

    services = am.calculate_output(
        root=tmp_path,
        files=["docs/README.md"],
        mode="docker",
        include_arch_test=False,
        fallback_task="serviceCi",
    )

    assert services == []