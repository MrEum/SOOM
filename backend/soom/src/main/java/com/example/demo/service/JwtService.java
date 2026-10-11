package com.example.demo.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final com.example.demo.repository.UserRepository users;

    public JwtService(
        @Value("${soom.jwt.secret:}") String secret,
        com.example.demo.repository.UserRepository users
    ) {
        this.users = users;
        byte[] bytes;
        if (secret.isBlank()) {
            bytes = new byte[32];
            new java.security.SecureRandom().nextBytes(bytes);
            org.slf4j.LoggerFactory.getLogger(JwtService.class).warn(
                "JWT_SECRET is unset: using a temporary development key; restarting invalidates logins."
            );
        } else {
            bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) throw new IllegalArgumentException(
                "JWT_SECRET must be Base64 of at least 32 random bytes"
            );
        }
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("soom"));
    }

    public String issue(int id, long seconds) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .issuer("soom")
            .subject(Integer.toString(id))
            .id(UUID.randomUUID().toString())
            .claim("credentials", credentials(id))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(seconds))
            .build();
        return encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    public Jwt validate(String value) {
        Jwt token = decoder.decode(value);
        if (
            token.getId() == null ||
            token.getExpiresAt() == null ||
            !Instant.now().isBefore(token.getExpiresAt()) ||
            revoked.containsKey(token.getId())
        ) throw new BadJwtException("Expired or revoked token");
        if (
            !credentials(Integer.parseInt(token.getSubject())).equals(
                token.getClaimAsString("credentials")
            )
        ) throw new BadJwtException("Credentials changed");
        return token;
    }

    private String credentials(int userId) {
        var user = users.findById(userId).orElseThrow(() -> new BadJwtException("User not found"));
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(
                user.password().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public void revoke(String value) {
        revoked.entrySet().removeIf(e -> !Instant.now().isBefore(e.getValue()));
        try {
            Jwt token = validate(value);
            revoked.put(token.getId(), token.getExpiresAt());
        } catch (JwtException ignored) {
            // 이미 무효인 토큰도 로그아웃은 완료한다.
        }
    }
}
