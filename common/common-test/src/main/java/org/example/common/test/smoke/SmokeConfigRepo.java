package org.example.common.test.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 스모크 테스트가 Config Server 없이 실제 {@code git-config-repo}의 설정으로 부팅하도록,
 * 저장소 내 {@code git-config-repo} 절대경로를 찾아 시스템 프로퍼티 {@code smoke.config.repo}로 노출한다.
 *
 * <p>스모크 테스트는 {@code spring.config.import=optional:file:${smoke.config.repo}/...} 로 필요한
 * 설정 파일만 import 한다. 정적 초기화라 {@code @SpringBootTest} 프로퍼티 해석 전에 값이 준비된다.
 */
public final class SmokeConfigRepo {

    public static final String PROPERTY = "smoke.config.repo";

    private static final String DIR_NAME = "git-config-repo";

    static {
        ensure();
    }

    private SmokeConfigRepo() {
    }

    /** 시스템 프로퍼티가 없으면 작업 디렉토리에서 위로 올라가며 git-config-repo를 찾아 설정한다. */
    public static synchronized void ensure() {
        if (System.getProperty(PROPERTY) != null) {
            return;
        }
        Path dir = locate();
        System.setProperty(PROPERTY, dir.toString());
    }

    private static Path locate() {
        Path current = Paths.get("").toAbsolutePath();
        for (Path p = current; p != null; p = p.getParent()) {
            Path candidate = p.resolve(DIR_NAME);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                DIR_NAME + " 디렉토리를 " + current + " 상위에서 찾지 못했다. 스모크 테스트 설정 소스를 확인한다.");
    }
}
