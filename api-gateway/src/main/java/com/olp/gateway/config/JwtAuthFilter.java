package com.olp.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String DEFAULT_SECRET_KEY_STRING = "ThisIsASecretKeyForEduLearnPlatformThatMustBeAtLeast32Bytes";

    @Value("${jwt.secret:${JWT_SECRET:" + DEFAULT_SECRET_KEY_STRING + "}}")
    private String secretKeyString = DEFAULT_SECRET_KEY_STRING;

    private static final List<String> OPEN_PATHS = List.of(
        "/auth/login",
        "/auth/register",
        "/auth/forgot-password",
        "/auth/reset-password",
        "/auth/validate",
        "/auth/public/",
        "/auth/login/success",
        "/oauth2/",
        "/login/",
        "/actuator/",
        "/v3/api-docs",
        "/swagger-ui"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (isOpenPath(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Unauthorized request without bearer token: {} {}", method, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            
            Object userIdObj = claims.get("userId");
            Long userId = null;
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                try {
                    userId = Long.parseLong((String) userIdObj);
                } catch (NumberFormatException ignored) {}
            }
            
            String name = claims.get("name", String.class);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Role", role != null ? role : "")
                    .header("X-User-Id", userId != null ? userId.toString() : "")
                    .header("X-User-Name", name != null ? name : "")
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            log.warn("JWT validation failed for {} {}: {}", method, path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isOpenPath(String path, HttpMethod method) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (OPEN_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && isPublicGetPath(path)) {
            return true;
        }
        return HttpMethod.POST.equals(method)
                && ("/payments/razorpay/webhook".equals(path) || "/payments/webhook".equals(path));
    }

    private boolean isPublicGetPath(String path) {
        return "/courses".equals(path)
                || "/courses/".equals(path)
                || path.matches("^/courses/\\d+$")
                || path.startsWith("/courses/published")
                || path.startsWith("/courses/featured")
                || path.startsWith("/courses/category/")
                || path.startsWith("/courses/search")
                || path.startsWith("/courses/uploads/")
                || path.startsWith("/enrollments/certificates/verify/");
    }

    private Key secretKey() {
        return Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
