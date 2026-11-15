package librosbuysan.auth.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import librosbuysan.auth.entity.RevokedToken;
import librosbuysan.auth.repository.RevokedTokenRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Lista de tokens revocados (R3 / RS28). Los JWT son stateless: el servidor no
 * guarda sesiones, asi que un token firmado sigue siendo valido hasta que
 * expira aunque el cliente lo borre. Para que el logout tenga efecto real
 * se guarda el jti de cada token cerrado y JwtAuthenticationFilter lo
 * consulta en cada request.
 *
 * <p>
 * Cada entrada se guarda solo hasta la expiracion del token: pasada esa
 * fecha el token ya es invalido por si mismo, asi que la lista nunca crece
 * mas alla de los tokens vigentes (maximo 60 min en COMPRADOR).
 *
 * <p>
 * Se persiste en la tabla {@code revoked_tokens} para que la revocacion
 * se mantenga incluso si se reinicia el backend antes de que el token expire.
 */
@Service
public class TokenRevocationService {

    // jti -> instante en que el token expira por si solo (usado en tests unitarios
    // sin BD)
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final RevokedTokenRepo revokedTokenRepo;

    public TokenRevocationService() {
        this.revokedTokenRepo = null;
    }

    @Autowired
    public TokenRevocationService(RevokedTokenRepo revokedTokenRepo) {
        this.revokedTokenRepo = revokedTokenRepo;
    }

    public void revoke(String tokenId, Instant expiresAt) {
        purgeExpired();
        // Si ya expiro no hace falta guardarlo: el filtro lo rechaza por expiracion
        if (expiresAt.isAfter(Instant.now())) {
            if (revokedTokenRepo != null) {
                if (!revokedTokenRepo.existsById(tokenId)) {
                    try {
                        revokedTokenRepo.saveAndFlush(new RevokedToken(tokenId, expiresAt));
                    } catch (DataIntegrityViolationException ignored) {
                        // Otro request concurrente ya revoco el mismo jti: operacion idempotente
                    }
                }
            } else {
                revoked.put(tokenId, expiresAt);
            }
        }
    }

    public boolean isRevoked(String tokenId) {
        if (revokedTokenRepo != null) {
            return revokedTokenRepo.existsByTokenIdAndExpiresAtAfter(tokenId, Instant.now());
        }
        return revoked.containsKey(tokenId);
    }

    // Limpieza oportunista en cada logout: evita configurar @Scheduled y el
    // conjunto es chico, asi que recorrerlo es barato
    private void purgeExpired() {
        Instant now = Instant.now();
        if (revokedTokenRepo != null) {
            revokedTokenRepo.deleteExpired(now);
        } else {
            revoked.values().removeIf(exp -> !exp.isAfter(now));
        }
    }
}