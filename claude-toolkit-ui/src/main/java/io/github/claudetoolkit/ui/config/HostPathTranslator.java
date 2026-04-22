package io.github.claudetoolkit.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * v4.4.x — Docker 컨테이너 환경에서 Windows 호스트 경로를 컨테이너 마운트
 * 경로로 자동 변환.
 *
 * <p><b>배경</b>: <code>docker-compose.yml</code> 이 다음과 같이 마운트:
 * <pre>
 *   volumes:
 *     - /c:/host/c:ro    (Windows C: 드라이브 → 컨테이너 /host/c/)
 *     - /d:/host/d:ro    (Windows D: 드라이브 → 컨테이너 /host/d/)
 * </pre>
 *
 * <p>그러나 사용자가 Settings 페이지에서 Windows 경로 그대로 입력하면
 * (예: <code>D:\eclipse_indongfn\workspace\IND_ERP</code>)
 * Linux 컨테이너 안에서 그 경로로 접근 불가:
 * <ul>
 *   <li>Java <code>new File("D:\...")</code> → 상대 경로로 해석</li>
 *   <li><code>getAbsolutePath()</code> → <code>/app/D:\...</code> (workdir + 원본)</li>
 * </ul>
 *
 * <p><b>해결</b>: 이 클래스가 자동으로 변환:
 * <pre>
 *   D:\eclipse_indongfn\workspace\IND_ERP
 *     → /host/d/eclipse_indongfn/workspace/IND_ERP
 *   C:\Users\foo\bar.java
 *     → /host/c/Users/foo/bar.java
 * </pre>
 *
 * <p>변환 조건:
 * <ol>
 *   <li>Linux 환경 (File.separator == '/')</li>
 *   <li>경로가 <code>X:</code> 또는 <code>X:\</code> 또는 <code>X:/</code> 로 시작</li>
 *   <li>변환 결과 <code>/host/x/...</code> 가 실제 디렉토리로 존재</li>
 * </ol>
 *
 * <p>위 조건 미충족 시 원본 경로 그대로 반환 (안전).
 */
public final class HostPathTranslator {

    private static final Logger log = LoggerFactory.getLogger(HostPathTranslator.class);
    private static final boolean IS_LINUX = "/".equals(File.separator);

    private HostPathTranslator() {}

    /**
     * 입력 경로를 분석하여 컨테이너 마운트 경로로 변환.
     * 변환 불필요 / 불가능한 경우 원본 반환.
     *
     * @param raw 사용자 입력 경로 (Windows 또는 POSIX)
     * @return 컨테이너에서 접근 가능한 경로 (또는 원본)
     */
    public static String translate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        String path = raw.trim();

        // Linux 컨테이너가 아니면 변환 불필요 (Windows 호스트에서 직접 실행 등)
        if (!IS_LINUX) return path;

        // Windows 드라이브 문자 패턴: X: 또는 X:\ 또는 X:/
        if (path.length() < 2 || path.charAt(1) != ':') return path;
        char drive = Character.toLowerCase(path.charAt(0));
        if (drive < 'a' || drive > 'z') return path;

        // 백슬래시를 슬래시로 통일
        String rest = path.substring(2).replace('\\', '/');
        if (rest.startsWith("/")) rest = rest.substring(1);

        String translated = "/host/" + drive + "/" + rest;

        // 검증: 변환된 경로가 실제로 존재하면 사용, 아니면 원본 반환
        File f = new File(translated);
        if (f.exists()) {
            log.debug("Windows 경로 자동 변환: '{}' → '{}'", raw, translated);
            return translated;
        }
        // 부모 디렉토리라도 있으면 사용 (서브디렉토리 미생성 케이스 대응)
        File parent = f.getParentFile();
        if (parent != null && parent.exists()) {
            log.debug("Windows 경로 자동 변환 (부모 존재): '{}' → '{}'", raw, translated);
            return translated;
        }
        // /host/x 디렉토리 자체가 있으면 (마운트는 됨) — 변환된 경로로 시도
        File mountRoot = new File("/host/" + drive);
        if (mountRoot.exists()) {
            log.debug("Windows 경로 자동 변환 (마운트만 존재): '{}' → '{}'", raw, translated);
            return translated;
        }
        // 마운트도 없음 — 변환 무의미. 원본 반환 (호출자가 적절한 에러 메시지 표시)
        log.debug("Windows 경로 변환 불가 (마운트 없음): {}", raw);
        return raw;
    }

    /** 경로가 Windows 스타일이고 Linux 환경인지 (변환 시도 가능 여부) */
    public static boolean isWindowsPathOnLinux(String raw) {
        if (!IS_LINUX || raw == null || raw.length() < 2) return false;
        return raw.charAt(1) == ':' &&
                Character.isLetter(raw.charAt(0));
    }

    /**
     * Settings 입력값과 변환된 경로 모두를 보여주는 디버그용 설명.
     * Hero status pill 의 hover 메시지 등에 사용 권장.
     */
    public static String describe(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "(미설정)";
        String translated = translate(raw);
        if (translated.equals(raw)) return raw;
        return raw + "  →  " + translated;
    }
}
