package org.example.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AnalyzeClasses(packages = "org.example.arch")
class PackageArchitectureTest {

    private static final List<ServiceBoundary> SERVICE_BOUNDARIES = List.of(
            new ServiceBoundary(
                    "chat",
                    "chat/chat-domain",
                    "chat/chat-application",
                    List.of("chat/chat-adapter-in", "chat/chat-adapter-out"),
                    "chat/chat-bootstrap",
                    List.of(
                            "org.example.chatmessage.domain..",
                            "org.example.chatroom.domain.."
                    )
            ),
            new ServiceBoundary(
                    "websocket-gateway",
                    null,
                    "websocket-gateway/websocket-gateway-application",
                    List.of(
                            "websocket-gateway/websocket-gateway-adapter-in",
                            "websocket-gateway/websocket-gateway-adapter-out"
                    ),
                    "websocket-gateway/websocket-gateway-bootstrap",
                    List.of()
            ),
            new ServiceBoundary(
                    "spring-cloud-config",
                    "spring-cloud-config/spring-cloud-config-domain",
                    "spring-cloud-config/spring-cloud-config-application",
                    List.of(
                            "spring-cloud-config/spring-cloud-config-adapter-in",
                            "spring-cloud-config/spring-cloud-config-adapter-out"
                    ),
                    "spring-cloud-config/spring-cloud-config-bootstrap",
                    List.of()
            ),
            new ServiceBoundary(
                    "user",
                    "user/user-domain",
                    "user/user-application",
                    List.of("user/user-adapter-in", "user/user-adapter-out"),
                    "user/user-bootstrap",
                    List.of(
                            "org.example.user.account.domain..",
                            "org.example.user.role.domain.."
                    )
            ),
            new ServiceBoundary(
                    "market",
                    "market/market-domain",
                    "market/market-application",
                    List.of("market/market-adapter-in", "market/market-adapter-out"),
                    "market/market-bootstrap",
                    List.of("org.example.market.domain..")
            ),
            new ServiceBoundary(
                    "notification",
                    "notification/notification-domain",
                    "notification/notification-application",
                    List.of("notification/notification-adapter-in", "notification/notification-adapter-out"),
                    "notification/notification-bootstrap",
                    List.of("org.example.notification.domain..")
            )
    );

    private static final List<String> APPLICATION_MODULE_DIRECTORIES = List.of(
            "chat/chat-application",
            "websocket-gateway/websocket-gateway-application",
            "oauth2-authorization-server/oauth2-authorization-server-application",
            "oauth2-client/oauth2-client-application",
            "spring-cloud-api-gateway/spring-cloud-api-gateway-application",
            "spring-cloud-config/spring-cloud-config-application",
            "user/user-application",
            "outbox-poller/outbox-poller-application",
            "market/market-application",
            "notification/notification-application"
    );

    private static final List<String> BOOTSTRAP_MODULE_DIRECTORIES = List.of(
            "chat/chat-bootstrap",
            "websocket-gateway/websocket-gateway-bootstrap",
            "oauth2-authorization-server/oauth2-authorization-server-bootstrap",
            "oauth2-client/oauth2-client-bootstrap",
            "spring-cloud-api-gateway/spring-cloud-api-gateway-bootstrap",
            "spring-cloud-config/spring-cloud-config-bootstrap",
            "user/user-bootstrap",
            "outbox-poller/outbox-poller-bootstrap",
            "market/market-bootstrap",
            "notification/notification-bootstrap"
    );

    @ArchTest
    static void common_modules_must_not_depend_on_service_packages(JavaClasses ignored) {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.example.chatmessage..",
                        "org.example.chatroom..",
                        "org.example.session..",
                        "org.example.user..",
                        "org.example.oauth2..",
                        "org.example.upbit..",
                        "org.example.vault..",
                        "org.example.contract.."
                );

        checkAllowingEmpty(rule, importMainClasses(List.of(
                "common/common-core",
                "common/common-jpa",
                "common/common-event",
                "common/common-web",
                "common/common-grpc",
                "common/common-id",
                "common/common-outbox",
                "common/common-redis",
                "common/common-util",
                "common/common-mongo",
                "common/common-test",
                "common/common-actuator-core",
                "common/common-actuator-webmvc",
                "common/common-actuator-webflux"
        )));
    }

    @ArchTest
    static void domain_modules_must_not_depend_on_adapter_or_infra_packages(JavaClasses ignored) {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in..",
                        "..adapter.out..",
                        "..infra.."
                );

        checkAllowingEmpty(rule, importMainClasses(List.of(
                "chat/chat-domain",
                "spring-cloud-config/spring-cloud-config-domain",
                "user/user-domain",
                "market/market-domain",
                "notification/notification-domain"
        )));
    }

    @ArchTest
    static void domain_modules_must_not_depend_on_other_service_domain_packages(JavaClasses ignored) {
        SERVICE_BOUNDARIES.stream()
                .filter(boundary -> !boundary.domainPackages().isEmpty())
                .forEach(boundary -> {
                    List<String> otherDomainPackages = SERVICE_BOUNDARIES.stream()
                            .filter(other -> !other.name().equals(boundary.name()))
                            .flatMap(other -> other.domainPackages().stream())
                            .toList();

                    if (otherDomainPackages.isEmpty()) {
                        return;
                    }

                    ArchRule rule = noClasses()
                            .should().dependOnClassesThat()
                            .resideInAnyPackage(otherDomainPackages.toArray(String[]::new));

                    checkAllowingEmpty(rule, importMainClasses(List.of(boundary.domainModuleDirectory())));
                });
    }

    @ArchTest
    static void application_modules_must_not_depend_on_adapter_in_or_out_packages(JavaClasses ignored) {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapter.in..",
                        "..adapter.out.."
                );

        checkAllowingEmpty(rule, importMainClasses(APPLICATION_MODULE_DIRECTORIES));
    }

    @ArchTest
    static void application_modules_must_not_depend_on_other_service_domain_packages(JavaClasses ignored) {
        SERVICE_BOUNDARIES.forEach(boundary -> {
            List<String> otherDomainPackages = SERVICE_BOUNDARIES.stream()
                    .filter(other -> !other.name().equals(boundary.name()))
                    .flatMap(other -> other.domainPackages().stream())
                    .toList();

            if (otherDomainPackages.isEmpty()) {
                return;
            }

            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(otherDomainPackages.toArray(String[]::new));

            checkAllowingEmpty(rule, importMainClasses(List.of(boundary.applicationModuleDirectory())));
        });
    }

    @ArchTest
    static void adapter_modules_must_not_depend_on_other_service_domain_packages(JavaClasses ignored) {
        SERVICE_BOUNDARIES.forEach(boundary -> {
            List<String> otherDomainPackages = SERVICE_BOUNDARIES.stream()
                    .filter(other -> !other.name().equals(boundary.name()))
                    .flatMap(other -> other.domainPackages().stream())
                    .toList();

            if (otherDomainPackages.isEmpty()) {
                return;
            }

            ArchRule rule = noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(otherDomainPackages.toArray(String[]::new));

            checkAllowingEmpty(rule, importMainClasses(boundary.adapterModuleDirectories()));
        });
    }

    @ArchTest
    static void bootstrap_modules_should_only_contain_application_entrypoints(JavaClasses ignored) {
        JavaClasses classes = importMainClasses(BOOTSTRAP_MODULE_DIRECTORIES);
        List<String> failures = new ArrayList<>();

        classes.forEach(javaClass -> {
            if (!javaClass.getSimpleName().equals("Main")) {
                failures.add(javaClass.getName()
                        + " is in a bootstrap module; bootstrap modules should only contain Main entrypoints");
            }
        });

        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private static void checkAllowingEmpty(ArchRule rule, JavaClasses classes) {
        rule.allowEmptyShould(true).check(classes);
    }

    private static JavaClasses importMainClasses(List<String> projectDirectories) {
        Path root = findRoot();

        List<Path> classDirectories = projectDirectories.stream()
                .map(directory -> root.resolve(directory).resolve("build/classes/java/main"))
                .filter(Files::isDirectory)
                .toList();

        return new ClassFileImporter().importPaths(classDirectories);
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
                "Cannot locate repository root from user.dir=" + System.getProperty("user.dir")
        );
    }

    private record ServiceBoundary(
            String name,
            String domainModuleDirectory,
            String applicationModuleDirectory,
            List<String> adapterModuleDirectories,
            String bootstrapModuleDirectory,
            List<String> domainPackages
    ) {
    }
}