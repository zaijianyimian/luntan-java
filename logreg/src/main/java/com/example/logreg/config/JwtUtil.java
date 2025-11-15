package com.example.logreg.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
@Scope("singleton")
public class JwtUtil {
    public JwtUtil() {
        log.error("JwtUtil has been created!");  // 添加日志调试
    }

    @Value("${jwt.token}")
    @Getter
    private String SECRET;
//    private final String SECRET = "YWFhYmJiY2NjZGRkZWVlZmZmMTIzNDU2Nzg5MGFiY2RlZmdo";//密钥
    private final long EXPIRAION  = 1000 * 60 * 60 * 24;

    private SecretKey getSignKey(){
        byte[] key = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(key);
    }
    public String generateToken(Long userId){
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRAION))
                .signWith(getSignKey())
                .compact();
    }
    //解析token
    public Claims parseToken(String token) {
        if(token == null || token.trim().isEmpty()){
            throw new IllegalArgumentException("token不能为空");
        }
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        String sub = parseToken(token).getSubject();
        return sub == null ? null : Long.parseLong(sub);
    }

    //验证token是否有效
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

}
