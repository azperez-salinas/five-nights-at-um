package librosbuysan.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * R3 / RS28: token revocado antes de su expiracion (por ejemplo al cerrar
 * sesion). Se persiste en base de datos para que la revocacion sobreviva
 * a reinicios del backend hasta que el token venza por si mismo.
 */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @Column(name = "token_id", nullable = false, length = 64, updatable = false)
    private String tokenId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected RevokedToken() {
        // requerido por JPA
    }

    public RevokedToken(String tokenId, Instant expiresAt) {
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

