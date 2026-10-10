package com.nexflow.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final long REFRESH_TTL_MS = 7L * 24 * 3600 * 1000;

    private final AppProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(String username, Long userId, List<String> roles, List<String> permissions) {
        return buildToken(username, userId, roles, permissions, props.getJwt().getExpirationMs(), "access");
    }

    public String issueRefreshToken(String username, Long userId) {
        return buildToken(username, userId, List.of(), List.of(), REFRESH_TTL_MS, "refresh");
    }

    public String buildToken(String username, Long userId, List<String> roles, List<String> permissions, long ttlMillis) {
        return buildToken(username, userId, roles, permissions, ttlMillis, "access");
    }

    private String buildToken(String username, Long userId, List<String> roles, List<String> permissions,
                              long ttlMillis, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
