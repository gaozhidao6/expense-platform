package com.shixi.expense.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    //定义请求头名称
    private static final String AUTHORIZATION_HEADER = "Authorization";
    //定义Token固定前缀
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        // 请求携带 Token，并且当前请求还没有完成认证时才进行解析。
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token);
        }
        // 无论是否携带 Token，都继续进入后续过滤器。
        // 业务接口是否允许匿名访问，由 SecurityConfig 决定。
        filterChain.doFilter(request, response);
    }

     /**
     * 从 Authorization 请求头中提取 Bearer Token。
     */
    private String resolveToken(HttpServletRequest request){
        //读取请求头
        String authorization =
                request.getHeader(AUTHORIZATION_HEADER);
        //检查请求头格式
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        //去除前缀
        String token =
                authorization.substring(BEARER_PREFIX.length()).trim();

        return StringUtils.hasText(token) ? token : null;
    }
     /**
     * 验证 Token 并把认证信息写入 SecurityContext。
     */
    private void authenticate(String token){
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            String username = claims.getSubject();

            List<?> roleClaims = claims.get("roles",List.class);
            List<SimpleGrantedAuthority> authorities =  
                    roleClaims == null ? List.of():roleClaims.stream()
                                        .map(String::valueOf).map(role -> new SimpleGrantedAuthority(
                                            "ROLE_" + role))
                                    .toList();
            //创建 Spring Security 的认证对象。
            UsernamePasswordAuthenticationToken authentication = 
                                            new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );
                    //告诉Spring Security当前已经通过认证
            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            // Token 过期、签名错误或格式错误时清除认证信息。
            // 后续访问受保护接口时，由 Spring Security 返回 401。
            SecurityContextHolder.clearContext();
        }
    }

}
