package com.pahal.billingApp.service;


import com.pahal.billingApp.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    // In production, move this to Azure Key Vault or environment variables
    private static final String SECRET_KEY = "your_very_secure_and_very_long_secret_key_here";

    public String extractTenantId(String token) {
        return (String) extractClaim(token, (Function<Claims, Object>) claims -> claims.get("tenantId", String.class));
    }

    // Add this to extract the role back out during request filtering
    public String extractRole(String token) {
        return (String) extractClaim(token, (Function<Claims, Object>) claims -> claims.get("role", String.class));
    }

    public String generateToken(String userId, String tenantId, Role userRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", tenantId); // This is the magic link
        claims.put("role", userRole);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }
}