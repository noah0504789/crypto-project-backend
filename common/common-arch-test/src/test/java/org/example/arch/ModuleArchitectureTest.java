package org.example.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleArchitectureTest {

    private static final Pattern INCLUDED_PROJECT = Pattern.compile("'([^']+)'|\"([^\"]+)\"");
    private static final Pattern PROJECT_DEPENDENCY = Pattern.compile("^\\s*(\\w+)\\s+project\\(['\"](:[^'\"]+)['\"]\\)");

    private static final Set<String> NON_SERVICE_PROJECTS = Set.of("common", "protobuf");
    private static final Set<String> SERVICE_IMPORT_PREFIXES = Set.of(
            "org.example.chatmessage",
            "org.example.chatroom",
            "org.example.session",
            "org.example.user",
            "org.example.oauth2",
            "org.example.upbit",
            "org.example.vault",
            "org.example.contract"
    );

    private final Path root = findRoot();
    private final Map<String, ProjectModule> modules = readIncludedProjects();
    private final Map<String, Set<String>> dependencies = readProjectDependencies();

    @Test
    void includedProjectsMustHaveMatchingDirectories() {
        List<String> failures = new ArrayList<>();

        modules.values().forEach(module -> {
            if (!Files.isDirectory(module.directory())) {
                failures.add(module.path() + " -> missing directory " + root.relativize(module.directory()));
            }
        });

        assertNoFailures(failures);
    }

    @Test
    void domainModulesMustNotDependOnApplicationAdapterOrBootstrapModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isDomain()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> target.isApplication() || target.isAdapter() || target.isBootstrap())
                    .forEach(target -> failures.add(from + " must not depend on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void domainModulesMustOnlyDependOnCommonProtobufAndContractModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isDomain()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> !target.isCommonProject())
                    .filter(target -> !target.isProtobufProject())
                    .filter(target -> !(target.isContract() && target.serviceName().equals(module.serviceName())))
                    .forEach(target -> failures.add(from + " must only depend on common/protobuf/own-contract modules, but depends on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void applicationModulesMustNotDependOnAdaptersOrBootstrapModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isApplication()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> target.isAdapter() || target.isBootstrap())
                    .forEach(target -> failures.add(from + " must not depend on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void applicationModulesMustOnlyDependOnCommonProtobufOwnDomainAndContractModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isApplication()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> !target.isCommonProject())
                    .filter(target -> !target.isProtobufProject())
                    .filter(target -> !target.isContract())
                    .filter(target -> !(target.isDomain() && target.serviceName().equals(module.serviceName())))
                    .forEach(target -> failures.add(from + " must only depend on common/protobuf/own-domain/contract modules, but depends on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void adapterModulesMustNotDependOnOtherAdaptersOrBootstrapModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isAdapter()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> target.isAdapter() || target.isBootstrap())
                    .forEach(target -> failures.add(from + " must not depend on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void adapterModulesMustOnlyDependOnCommonProtobufOwnApplicationOwnDomainAndContractModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isAdapter()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> !target.isCommonProject())
                    .filter(target -> !target.isProtobufProject())
                    .filter(target -> !target.isContract())
                    .filter(target -> !target.isClient())
                    .filter(target -> !(target.serviceName().equals(module.serviceName()) && (target.isApplication() || target.isDomain())))
                    .forEach(target -> failures.add(from + " must only depend on common/protobuf/own-application/own-domain/contract modules, but depends on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void bootstrapModulesMustOnlyDependOnCommonProtobufOwnServiceModulesAndContractModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isBootstrap()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> !target.isCommonProject())
                    .filter(target -> !target.isProtobufProject())
                    .filter(target -> !target.isContract())
                    .filter(target -> !target.serviceName().equals(module.serviceName()))
                    .forEach(target -> failures.add(from + " must only depend on common/protobuf/own-service/contract modules, but depends on " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void serviceModulesMustNotDependOnOtherServiceImplementationModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || module.isAggregateWithChildren(modules.keySet()) || !module.isServiceProject()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(target -> target.isServiceProject() && !target.serviceName().equals(module.serviceName()))
                    .filter(target -> !target.isContract())
                    .filter(target -> !target.isClient())
                    .forEach(target -> failures.add(from + " must not depend on other service implementation module " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void commonModulesMustNotDependOnServiceModules() {
        List<String> failures = new ArrayList<>();

        dependencies.forEach((from, targets) -> {
            ProjectModule module = modules.get(from);
            if (module == null || !module.isCommonProject()) {
                return;
            }

            targets.stream()
                    .map(modules::get)
                    .filter(Objects::nonNull)
                    .filter(ProjectModule::isServiceProject)
                    .forEach(target -> failures.add(from + " must not depend on service module " + target.path()));
        });

        assertNoFailures(failures);
    }

    @Test
    void commonSourceMustNotImportServicePackages() throws IOException {
        List<String> failures = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root.resolve("common"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .forEach(path -> inspectCommonImports(path, failures));
        }

        assertNoFailures(failures);
    }

    @Test
    void projectDependencyGraphMustNotHaveCycles() {
        List<String> failures = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();

        modules.keySet().forEach(module -> detectCycle(module, visited, visiting, stack, failures));

        assertNoFailures(failures);
    }

    private void inspectCommonImports(Path path, List<String> failures) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex).trim();
                if (!line.startsWith("import ")) {
                    continue;
                }

                for (String prefix : SERVICE_IMPORT_PREFIXES) {
                    if (line.startsWith("import " + prefix)) {
                        failures.add(root.relativize(path) + ":" + (lineIndex + 1) + " imports service package: " + line);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect " + path, e);
        }
    }

    private void detectCycle(
            String module,
            Set<String> visited,
            Set<String> visiting,
            ArrayDeque<String> stack,
            List<String> failures
    ) {
        if (visited.contains(module)) {
            return;
        }
        if (!visiting.add(module)) {
            failures.add(formatCycle(stack, module));
            return;
        }

        stack.addLast(module);
        dependencies.getOrDefault(module, Set.of()).stream()
                .filter(modules::containsKey)
                .forEach(target -> detectCycle(target, visited, visiting, stack, failures));
        stack.removeLast();

        visiting.remove(module);
        visited.add(module);
    }

    private String formatCycle(ArrayDeque<String> stack, String repeatedModule) {
        List<String> cycle = new ArrayList<>();
        boolean inCycle = false;
        for (String module : stack) {
            if (module.equals(repeatedModule)) {
                inCycle = true;
            }
            if (inCycle) {
                cycle.add(module);
            }
        }
        cycle.add(repeatedModule);
        return String.join(" -> ", cycle);
    }

    private Map<String, ProjectModule> readIncludedProjects() {
        try {
            Map<String, ProjectModule> result = new LinkedHashMap<>();
            for (String line : Files.readAllLines(root.resolve("settings.gradle"))) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("include ")) {
                    continue;
                }

                Matcher matcher = INCLUDED_PROJECT.matcher(trimmed);
                while (matcher.find()) {
                    String included = Optional.ofNullable(matcher.group(1)).orElse(matcher.group(2));
                    String path = ":" + included;
                    result.put(path, new ProjectModule(path, root.resolve(included.replace(':', '/'))));
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read settings.gradle", e);
        }
    }

    private Map<String, Set<String>> readProjectDependencies() {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        modules.values().forEach(module -> {
            Path buildFile = module.directory().resolve("build.gradle");
            if (!Files.isRegularFile(buildFile)) {
                result.put(module.path(), Set.of());
                return;
            }

            try {
                Set<String> targets = new LinkedHashSet<>();
                for (String line : Files.readAllLines(buildFile)) {
                    Matcher matcher = PROJECT_DEPENDENCY.matcher(line);
                    if (matcher.find() && !matcher.group(1).startsWith("test")) {
                        targets.add(matcher.group(2));
                    }
                }
                result.put(module.path(), Collections.unmodifiableSet(targets));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + buildFile, e);
            }
        });

        return Collections.unmodifiableMap(result);
    }

    private Path findRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from user.dir=" + System.getProperty("user.dir"));
    }

    private void assertNoFailures(List<String> failures) {
        assertTrue(failures.isEmpty(), () -> String.join(System.lineSeparator(), failures));
    }

    private record ProjectModule(String path, Path directory) {

        boolean isCommonProject() {
            return path.equals(":common") || path.startsWith(":common:");
        }

        boolean isServiceProject() {
            return !NON_SERVICE_PROJECTS.contains(serviceName());
        }

        boolean isProtobufProject() {
            return path.equals(":protobuf");
        }

        String serviceName() {
            String[] parts = path.substring(1).split(":");
            return parts[0];
        }

        boolean isAggregateWithChildren(Set<String> allProjectPaths) {
            String childPrefix = path + ":";
            return allProjectPaths.stream().anyMatch(candidate -> candidate.startsWith(childPrefix));
        }

        boolean isDomain() {
            return leafName().endsWith("-domain");
        }

        boolean isApplication() {
            return leafName().endsWith("-application");
        }

        boolean isAdapter() {
            return leafName().endsWith("-adapter-in") || leafName().endsWith("-adapter-out");
        }

        boolean isBootstrap() {
            return leafName().endsWith("-bootstrap");
        }

        boolean isContract() {
            return leafName().endsWith("-contract");
        }

        boolean isClient() {
            return leafName().endsWith("-client");
        }

        private String leafName() {
            int index = path.lastIndexOf(':');
            return path.substring(index + 1);
        }
    }
}
