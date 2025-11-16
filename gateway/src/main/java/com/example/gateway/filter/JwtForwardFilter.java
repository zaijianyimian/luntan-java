package com.example.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtForwardFilter implements GlobalFilter, Ordered {

    @Value("${jwt.token:}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ") && secret != null && !secret.isBlank()) {
            try {
                String token = auth.substring(7);
                if (token.indexOf('.') > 0 && token.indexOf('.', token.indexOf('.') + 1) > 0) {
                    Claims claims = Jwts.parserBuilder()
                            .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                            .build()
                            .parseClaimsJws(token)
                            .getBody();
                    Object sub = claims.get("sub");
                    Object userId = claims.get("userId");
                    String uid = sub != null ? String.valueOf(sub) : (userId != null ? String.valueOf(userId) : null);
                    if (uid != null) {
                        exchange.getRequest().mutate().header("X-User-Id", uid).build();
                    }
                }
            } catch (Exception e) {
                return chain.filter(exchange);
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() { return -100; }
}