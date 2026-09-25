package librosbuysan.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import librosbuysan.user.User;

@Service
public class JwtService {

    public static final String ROLE_CLAIM = "role";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long compradorExpirationSeconds;
    private final long duenoExpirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes.comprador:60}") long compradorMinutes,
            @Value("${app.jwt.expiration-minutes.dueno:15}") long duenoMinutes) {
        // Los mensajes de error nunca incluyen el valor del secreto
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) no esta configurado");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException e) {
            // Sin encadenar la causa: su mensaje podria contener parte del secreto
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) no es un valor base64 valido");
        }
        try {
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "app.jwt.secret (JWT_SECRET) debe tener al menos " + MIN_SECRET_BYTES + " bytes decodificado");
            }
            // SecretKeySpec hace su propia copia de la clave
            this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }

        if (compradorMinutes <= 0 || duenoMinutes <= 0) {
            throw new IllegalStateException("app.jwt.expiration-minutes.* debe ser mayor a 0");
        }
        this.compradorExpirationSeconds = compradorMinutes * 60;
        this.duenoExpirationSeconds = duenoMinutes * 60;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(getExpirationSeconds(user.getRole()));
        return Jwts.builder()
                .subject(Long.toString(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getExpirationSeconds(User.Role role) {
        return switch (role) {
            case COMPRADOR -> compradorExpirationSeconds;
            case DUENO -> duenoExpirationSeconds;
        };
    }

    /**
     * Verifica firma y expiracion y devuelve los claims. Lanza
     * {@link io.jsonwebtoken.JwtException} si el token no es valido.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
