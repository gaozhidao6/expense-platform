package com.shixi.expense.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTests {

    /** 测试密钥仅用于单元测试，长度满足 HS256 至少 32 字节的要求。 */
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "expense-platform-jwt-test-secret-32-bytes".getBytes(StandardCharsets.UTF_8));

    @Test
    void generatesAndParsesToken() {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 7200);

        String token = provider.generateToken(7L, "admin", List.of("ADMIN"));
        Claims claims = provider.parseToken(token);

        assertTrue(provider.isValid(token));
        assertEquals("admin", claims.getSubject());
        assertEquals("admin", provider.getUsername(token));
        assertEquals(7L, provider.getUserId(token));
        assertEquals(List.of("ADMIN"), claims.get("roles", List.class));
        assertEquals(List.of("ADMIN"), provider.getRoles(token));
        assertEquals(7200, provider.getExpirationSeconds());
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, 7200);
        String token = provider.generateToken(7L, "admin", List.of("ADMIN"));

        // 修改签名段中间的字符，确保一定改变实际签名字节。
        int signatureStart = token.lastIndexOf('.') + 1;
        int signatureIndex = signatureStart + 1;
        char original = token.charAt(signatureIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tamperedToken = token.substring(0, signatureIndex)
                + replacement
                + token.substring(signatureIndex + 1);

        assertFalse(provider.isValid(tamperedToken));
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_SECRET, -1);
        String token = provider.generateToken(7L, "admin", List.of("ADMIN"));

        assertFalse(provider.isValid(token));
    }
}
