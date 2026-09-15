package com.shixi.expense.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 访问令牌工具。
 *
 * <p>该类只负责生成、解析和验证令牌，不负责查询用户或校验登录密码。</p>
 */
@Component
public class JwtTokenProvider {

    /** JWT 签名和验签使用的 HMAC 密钥。 */
    private final SecretKey secretKey;

    /** 访问令牌的有效时间，单位为秒。 */
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * 为登录成功的用户生成访问令牌。
     *
     * @param userId 用户主键 ID
     * @param username 登录用户名
     * @param roles 用户角色编码，例如 ADMIN、EMPLOYEE
     * @return 已签名的 JWT 字符串
     */
    public String generateToken(Long userId, String username, List<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

        return Jwts.builder()
                // subject 表示令牌主体，这里使用登录用户名。
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 验证签名和有效期，并返回令牌中的声明。
     *
     * <p>令牌格式错误、签名错误或已经过期时，JJWT 会抛出 JwtException。</p>
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 判断令牌格式、签名和有效期是否都正确。 */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /** 读取令牌中的登录用户名。 */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /** 读取令牌中的用户主键 ID。 */
    public Long getUserId(String token) {
        Number userId = parseToken(token).get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    /** 读取令牌中的角色编码列表。 */
    public List<String> getRoles(String token) {
        Object roles = parseToken(token).get("roles");
        if (!(roles instanceof List<?> roleList)) {
            return List.of();
        }

        return roleList.stream()
                .map(String::valueOf)
                .toList();
    }

    /** 返回配置的令牌有效时间，供登录响应使用。 */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
