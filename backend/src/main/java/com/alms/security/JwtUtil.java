package com.alms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")   private String secret;
    @Value("${jwt.expiration}") private long expiration;

    public String generate(UserDetails u) { return generate(new HashMap<>(), u); }
    public String generate(Map<String, Object> claims, UserDetails u) {
        return Jwts.builder().setClaims(claims).setSubject(u.getUsername())
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key(), SignatureAlgorithm.HS256).compact();
    }
    public boolean isValid(String token, UserDetails u) {
        return extractUsername(token).equals(u.getUsername()) && !isExpired(token);
    }
    public String extractUsername(String token) { return claim(token, Claims::getSubject); }
    private boolean isExpired(String token)      { return claim(token, Claims::getExpiration).before(new Date()); }
    private <T> T claim(String t, Function<Claims, T> f) { return f.apply(all(t)); }
    private Claims all(String t) {
        return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(t).getBody();
    }
    private Key key() { return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)); }
}
