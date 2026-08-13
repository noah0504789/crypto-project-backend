package org.example.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@AnalyzeClasses(packages = "org.example.arch")
class PackageArchitectureTest {

    private static final String LEGACY_MARKET_DETECTION_BOOTSTRAP =
            "market-detection/market-detection-bootstrap";

    private static final List<ServiceBoundary> SERVICE_BOUNDARIES =
            List.of(
                    new ServiceBoundary(
                            "chat",
                            "chat/chat-domain",
                            "chat/chat-application",
                            List.of("chat/chat-adapter-in", "chat/chat-adapter-out"),
                            "chat/chat-bootstrap",
                            List.of(
                                    "org.example.chatmessage.domain..",
                                    "org.example.chatroom.domain..")),
                    new ServiceBoundary(
                            "websocket-gateway",
                            null,
                            "websocket-gateway/websocket-gateway-application",
                            List.of(
                                    "websocket-gateway/websocket-gateway-adapter-in",
                                    "websocket-gateway/websocket-gateway-adapter-out"),
                            "websocket-gateway/websocket-gateway-bootstrap",
                            List.of()),
                    new ServiceBoundary(
                            "spring-cloud-config",
                            null,
                            "spring-cloud-config/spring-cloud-config-application",
                            List.of(
                                    "spring-cloud-config/spring-cloud-config-adapter-in",
                                    "spring-cloud-config/spring-cloud-config-adapter-out"),
                            "spring-cloud-config/spring-cloud-config-bootstrap",
                            List.of()),
                    new ServiceBoundary(
                            "user",
                            "user/user-domain",
                            "user/user-application",
                            List.of("user/user-adapter-in", "user/user-adapter-out"),
                            "user/user-bootstrap",
                            List.of(
                                    "org.example.user.account.domain..",
                                    "org.example.user.role.domain..")),
                    new ServiceBoundary(
                            "market",
                            "market/market-domain",
                            "market/market-application",
                            List.of("market/market-adapter-in", "market/market-adapter-out"),
                            "market/market-bootstrap",
                            List.of("org.example.market.domain..")),
                    new ServiceBoundary(
                            "notification",
                            "notification/notification-domain",
                            "notification/notification-application",
                            List.of(
                                    "notification/notification-adapter-in",
                                    "notification/notification-adapter-out"),
                            "notification/notification-bootstrap",
                            List.of("org.example.notification.domain..")));

    @ArchTest
    static void common_modules_must_not_depend_on_service_packages(JavaClasses ignored) {
        ArchRule rule =
                noClasses()
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.example.chatmessage..",
                                "org.example.chatroom..",
                                "org.example.session..",
                                "org.example.user..",
                                "org.example.oauth2..",
                                "org.example.upbit..",
                                "org.example.vault..",
                                "org.example.contract..");

        checkAllowingEmpty(rule, importMainClasses(commonModuleDirectories()));
    }

    @ArchTest
    static void domain_modules_must_not_depend_on_adapter_or_infra_packages(JavaClasses ignored) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..adapter.in..", "..adapter.out..", "..infra..");

        checkAllowingEmpty(rule, importMainClasses(moduleDirectoriesEndingWith("-domain")));
    }

    @ArchTest
    static void domain_modules_must_not_depend_on_other_service_domain_packages(
            JavaClasses ignored) {
        SERVICE_BOUNDARIES.stream()
                .filter(boundary -> !boundary.domainPackages().isEmpty())
                .forEach(
                        boundary -> {
                            List<String> otherDomainPackages =
                                    SERVICE_BOUNDARIES.stream()
                                            .filter(other -> !other.name().equals(boundary.name()))
                                            .flatMap(other -> other.domainPackages().stream())
                                            .toList();

                            if (otherDomainPackages.isEmpty()) {
                                return;
                            }

                            ArchRule rule =
                                    noClasses()
                                            .should()
                                            .dependOnClassesThat()
                                            .resideInAnyPackage(
                                                    otherDomainPackages.toArray(String[]::new));

                            checkAllowingEmpty(
                                    rule,
                                    importMainClasses(List.of(boundary.domainModuleDirectory())));
                        });
    }

    @ArchTest
    static void application_modules_must_not_depend_on_adapter_in_or_out_packages(
            JavaClasses ignored) {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..application..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("..adapter.in..", "..adapter.out..");

        checkAllowingEmpty(rule, importMainClasses(moduleDirectoriesEndingWith("-application")));
    }

    @ArchTest
    static void application_modules_must_not_depend_on_other_service_domain_packages(
            JavaClasses ignored) {
        SERVICE_BOUNDARIES.forEach(
                boundary -> {
                    List<String> otherDomainPackages =
                            SERVICE_BOUNDARIES.stream()
                                    .filter(other -> !other.name().equals(boundary.name()))
                                    .flatMap(other -> other.domainPackages().stream())
                                    .toList();

                    if (otherDomainPackages.isEmpty()) {
                        return;
                    }

                    ArchRule rule =
                            noClasses()
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(otherDomainPackages.toArray(String[]::new));

                    checkAllowingEmpty(
                            rule,
                            importMainClasses(List.of(boundary.applicationModuleDirectory())));
                });
    }

    @ArchTest
    static void adapter_modules_must_not_depend_on_other_service_domain_packages(
            JavaClasses ignored) {
        SERVICE_BOUNDARIES.forEach(
                boundary -> {
                    List<String> otherDomainPackages =
                            SERVICE_BOUNDARIES.stream()
                                    .filter(other -> !other.name().equals(boundary.name()))
                                    .flatMap(other -> other.domainPackages().stream())
                                    .toList();

                    if (otherDomainPackages.isEmpty()) {
                        return;
                    }

                    ArchRule rule =
                            noClasses()
                                    .should()
                                    .dependOnClassesThat()
                                    .resideInAnyPackage(otherDomainPackages.toArray(String[]::new));

                    checkAllowingEmpty(
                            rule, importMainClasses(boundary.adapterModuleDirectories()));
                });
    }

    @ArchTest
    static void bootstrap_modules_should_only_contain_application_entrypoints(JavaClasses ignored) {
        JavaClasses classes = importMainClasses(bootstrapModuleDirectories());
        List<String> failures = new ArrayList<>();

        classes.forEach(
                javaClass -> {
                    if (!javaClass.getSimpleName().equals("Main")) {
                        failures.add(
                                javaClass.getName()
                                        + " is in a bootstrap module; bootstrap modules should only contain Main entrypoints");
                    }
                });

        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private static void checkAllowingEmpty(ArchRule rule, JavaClasses classes) {
        rule.allowEmptyShould(true).check(classes);
    }

    /** common/ 하위 모듈 디렉토리를 스캔한다. 하드코딩하면 새 모듈이 조용히 검사에서 빠진다. */
    private static List<String> commonModuleDirectories() {
        Path commonRoot = findRoot().resolve("common");

        try (Stream<Path> dirs = Files.list(commonRoot)) {
            return dirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("common-"))
                    .filter(name -> !name.equals("common-arch-test"))
                    .sorted()
                    .map(name -> "common/" + name)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("common 모듈 디렉토리를 읽지 못했다: " + commonRoot, e);
        }
    }

    private static List<String> moduleDirectoriesEndingWith(String suffix) {
        return moduleDirectories().stream()
                .filter(directory -> Path.of(directory).getFileName().toString().endsWith(suffix))
                .toList();
    }

    private static List<String> bootstrapModuleDirectories() {
        return moduleDirectoriesEndingWith("-bootstrap").stream()
                // market-detection is a documented legacy monolith; see ModuleArchitectureTest.
                .filter(directory -> !directory.equals(LEGACY_MARKET_DETECTION_BOOTSTRAP))
                .toList();
    }

    private static List<String> moduleDirectories() {
        Path root = findRoot();

        try (Stream<Path> dirs = Files.walk(root, 2)) {
            return dirs.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("build.gradle")))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(directory -> directory.replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("모듈 디렉터리를 읽지 못했다: " + root, e);
        }
    }

    private static JavaClasses importMainClasses(List<String> projectDirectories) {
        Path root = findRoot();

        List<Path> requestedClassDirectories =
                projectDirectories.stream()
                        .map(
                                directory ->
                                        root.resolve(directory).resolve("build/classes/java/main"))
                        .toList();
        List<Path> missingClassDirectories =
                requestedClassDirectories.stream().filter(Files::notExists).toList();

        if (!missingClassDirectories.isEmpty()) {
            String missing =
                    missingClassDirectories.stream()
                            .map(root::relativize)
                            .map(Path::toString)
                            .sorted()
                            .reduce((left, right) -> left + ", " + right)
                            .orElseThrow();
            throw new IllegalStateException(
                    "Architecture test skipped compiled classes: "
                            + missing
                            + ". Ensure :common:common-arch-test:test depends on their classes tasks.");
        }

        return new ClassFileImporter().importPaths(requestedClassDirectories);
    }

    private static Path findRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException(
                "Cannot locate repository root from user.dir=" + System.getProperty("user.dir"));
    }

    private record ServiceBoundary(
            String name,
            String domainModuleDirectory,
            String applicationModuleDirectory,
            List<String> adapterModuleDirectories,
            String bootstrapModuleDirectory,
            List<String> domainPackages) {}
}
