package org.example.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * docs/CODE_STYLE.md 중 기계로 확인 가능한 항목을 강제한다. 줄바꿈·주석처럼 판단이 필요한 항목은 verify-change 절차에서 사람이 본다.
 */
class CodeStyleArchitectureTest {

    private static final String GENERATED_GRPC_PACKAGE = "org.example.grpc";

    private static final Set<String> SPRING_BEAN_ANNOTATIONS =
            Set.of(
                    "org.springframework.stereotype.Component",
                    "org.springframework.stereotype.Service",
                    "org.springframework.stereotype.Repository",
                    "org.springframework.context.annotation.Configuration",
                    "org.springframework.web.bind.annotation.RestController",
                    "org.springframework.web.bind.annotation.RestControllerAdvice");

    @Test
    @DisplayName("production 멤버는 접근제어자를 생략하지 않는다")
    void members_should_declare_explicit_access_modifier() {
        List<String> failures = new ArrayList<>();

        importMainClasses().forEach(javaClass -> {
            if (isSkipped(javaClass)) {
                return;
            }

            javaClass.getFields().stream()
                    .filter(this::isImplicitPackagePrivate)
                    .forEach(field -> failures.add(field.getFullName()));

            javaClass.getMethods().stream()
                    .filter(this::isImplicitPackagePrivate)
                    .forEach(method -> failures.add(method.getFullName()));
        });

        assertTrue(
                failures.isEmpty(),
                () ->
                        "접근제어자를 명시한다(public/private/protected). Java에는 package-private 키워드가 없어"
                                + " 생략하면 의도인지 실수인지 구분되지 않는다:"
                                + System.lineSeparator()
                                + String.join(System.lineSeparator(), failures));
    }

    @Test
    @DisplayName("스프링 빈은 생성자를 하나만 가진다")
    void spring_beans_should_have_single_constructor() {
        List<String> failures = new ArrayList<>();

        importMainClasses().forEach(javaClass -> {
            if (isSkipped(javaClass) || !isSpringBean(javaClass)) {
                return;
            }

            if (javaClass.getConstructors().size() > 1) {
                failures.add(javaClass.getName() + " (생성자 " + javaClass.getConstructors().size() + "개)");
            }
        });

        assertTrue(
                failures.isEmpty(),
                () ->
                        "스프링이 주입 생성자를 고르지 못해 부팅이 깨진다. 생성자는 하나만 두고 @RequiredArgsConstructor 를 쓴다"
                                + "(테스트 편의용 보조 생성자 금지):"
                                + System.lineSeparator()
                                + String.join(System.lineSeparator(), failures));
    }

    private boolean isSkipped(JavaClass javaClass) {
        return javaClass.isRecord()
                || javaClass.isEnum()
                || javaClass.isInterface()
                || javaClass.isAnonymousClass()
                || javaClass.getName().contains("$")
                || javaClass.getPackageName().startsWith(GENERATED_GRPC_PACKAGE);
    }

    private boolean isSpringBean(JavaClass javaClass) {
        return javaClass.getAnnotations().stream()
                .map(annotation -> annotation.getRawType().getName())
                .anyMatch(SPRING_BEAN_ANNOTATIONS::contains);
    }

    private boolean isImplicitPackagePrivate(JavaMember member) {
        Set<JavaModifier> modifiers = member.getModifiers();

        return !modifiers.contains(JavaModifier.PUBLIC)
                && !modifiers.contains(JavaModifier.PRIVATE)
                && !modifiers.contains(JavaModifier.PROTECTED)
                && !modifiers.contains(JavaModifier.SYNTHETIC);
    }

    /** 하드코딩하면 새 모듈이 조용히 검사에서 빠진다. 빌드 산출물 디렉터리를 훑어 모든 모듈을 대상으로 삼는다. */
    private JavaClasses importMainClasses() {
        Path root = findRoot();
        List<Path> classDirectories = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root, 8)) {
            paths.filter(path -> path.endsWith(Path.of("build/classes/java/main")))
                    .filter(Files::isDirectory)
                    .forEach(classDirectories::add);
        } catch (IOException e) {
            throw new IllegalStateException("빌드 산출물 디렉터리를 읽지 못했다: " + root, e);
        }

        return new ClassFileImporter().importPaths(classDirectories.toArray(new Path[0]));
    }

    private Path findRoot() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }

        if (current == null) {
            throw new IllegalStateException("settings.gradle 을 찾지 못했다.");
        }

        return current;
    }
}
