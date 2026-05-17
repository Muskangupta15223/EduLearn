package com.olp.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    private static final String SECRET = "ThisIsASecretKeyForEduLearnPlatformThatMustBeAtLeast32Bytes";

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "secretKeyString", SECRET);
    }

    @Test
    void allowsConfiguredOpenPathsWithoutAuthorizationHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/courses/published").build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertTrue(chain.invoked);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsProtectedPathWithoutBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/payments/me").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsInvalidBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/payments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                        .build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void forwardsClaimsAsHeadersForValidToken() {
        String token = Jwts.builder()
                .setSubject("student@example.com")
                .claim("role", "STUDENT")
                .claim("userId", 42L)
                .claim("name", "Asha")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/payments/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertTrue(chain.invoked);
        ServerWebExchange forwarded = chain.exchange;
        assertEquals("student@example.com", forwarded.getRequest().getHeaders().getFirst("X-User-Email"));
        assertEquals("STUDENT", forwarded.getRequest().getHeaders().getFirst("X-User-Role"));
        assertEquals("42", forwarded.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("Asha", forwarded.getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals(-1, filter.getOrder());
    }

    @Test
    void treatsOptionsRequestsAsOpen() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/payments/me").build()
        );
        CapturingChain chain = new CapturingChain();

        filter.filter(exchange, chain).block();

        assertTrue(chain.invoked);
    }

    private static final class CapturingChain implements GatewayFilterChain {
        private boolean invoked;
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.invoked = true;
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
