package librosbuysan.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Lista de tokens revocados (R3). Los JWT son stateless: el servidor no
 * guarda sesiones, asi que un token firmado sigue siendo valido hasta que
 * expira aunque el cliente lo borre. Para que el logout tenga efecto real
 * se guarda el jti de cada token cerrado y JwtAuthenticationFilter lo
 * consulta en cada request.
 *
 * <p>Cada entrada se guarda solo hasta la expiracion del token: pasada esa
 * fecha el token ya es invalido por si mismo, asi que la lista nunca crece
 * mas alla de los tokens vigentes (maximo 60 min en COMPRADOR).
 *
 * <p>Limitacion conocida: vive en memoria. Un reinicio del backend la vacia
 * y con varias instancias no se comparte. Para este proyecto (un solo
 * contenedor) alcanza; si escala, se reemplaza por una tabla o Redis sin
 * tocar a los llamadores.
 */
@Service
public class TokenRevocationService {

    // jti -> instante en que el token expira por si solo
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        purgeExpired();
        // Si ya expiro no hace falta guardarlo: el filtro lo rechaza por expiracion
        if (expiresAt.isAfter(Instant.now())) {
            revoked.put(tokenId, expiresAt);
        }
    }

    public boolean isRevoked(String tokenId) {
        return revoked.containsKey(tokenId);
    }

    // Limpieza oportunista en cada logout: evita configurar @Scheduled y el
    // mapa es chico, asi que recorrerlo es barato
    private void purgeExpired() {
        Instant now = Instant.now();
        revoked.values().removeIf(exp -> !exp.isAfter(now));
    }
}