package io.github.claudetoolkit.ui.security;

/**
 * 사용자별 IP 화이트리스트 검사 유틸리티 (v2.6.0).
 *
 * <p>지원 형식 (콤마로 구분):
 * <ul>
 *   <li>단일 IPv4: {@code 192.168.1.10}</li>
 *   <li>CIDR: {@code 192.168.1.0/24}, {@code 10.0.0.0/8}</li>
 *   <li>빈 문자열 또는 null → 모든 IP 허용</li>
 * </ul>
 *
 * <p>순수 Java 비트 연산 기반 IPv4 CIDR 매칭 (외부 라이브러리 불필요).
 */
public final class IpWhitelistChecker {

    private IpWhitelistChecker() {}

    /**
     * 클라이언트 IP가 화이트리스트에 포함되는지 검사합니다.
     *
     * @param clientIp  검사할 IP 주소 (예: "192.168.1.10")
     * @param whitelist 콤마 구분 화이트리스트 문자열 (빈 값이면 허용)
     * @return 허용 여부
     */
    public static boolean isAllowed(String clientIp, String whitelist) {
        if (whitelist == null || whitelist.trim().isEmpty()) return true;  // 빈 → 무제한
        if (clientIp == null || clientIp.trim().isEmpty()) return false;

        // IPv6 localhost 정규화
        String normalizedIp = clientIp;
        if ("0:0:0:0:0:0:0:1".equals(normalizedIp) || "::1".equals(normalizedIp)) {
            normalizedIp = "127.0.0.1";
        }

        long ipLong;
        try {
            ipLong = ipv4ToLong(normalizedIp);
        } catch (Exception e) {
            return false;  // IPv4가 아니면 거부
        }

        String[] entries = whitelist.split(",");
        for (String entry : entries) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            try {
                if (e.contains("/")) {
                    // CIDR 형식
                    if (matchesCidr(ipLong, e)) return true;
                } else {
                    // 단일 IP
                    if (ipLong == ipv4ToLong(e)) return true;
                }
            } catch (Exception ignored) {
                // 잘못된 엔트리는 건너뛰기
            }
        }
        return false;
    }

    /** IPv4 문자열 → 32비트 long 변환 */
    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4: " + ip);
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) throw new IllegalArgumentException("Invalid octet: " + octet);
            result = (result << 8) | octet;
        }
        return result;
    }

    /** CIDR 매칭: {@code ip & mask == network} */
    private static boolean matchesCidr(long ipLong, String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) return false;
        long network = ipv4ToLong(parts[0]);
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 32) return false;
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (ipLong & mask) == (network & mask);
    }
}
