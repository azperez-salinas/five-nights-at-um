package librosbuysan.security;

import java.time.Instant;
import librosbuysan.user.entity.User;

/**
 * Principal que arma JwtAuthenticationFilter a partir de un token ya validado.
 * tokenId (claim jti) y tokenExpiresAt viajan aca para que el logout sepa
 * que token revocar sin tener que volver a parsear el header Authorization.
 *
 * passwordChangeRequired (R18): el token es de un dueno que todavia tiene la
 * contrasena temporal. Al cambiarla, ese token se revoca.
 */
public record AuthenticatedUser(Long id, User.Role role, String tokenId, Instant tokenExpiresAt,
                                boolean passwordChangeRequired) {

    public AuthenticatedUser(Long id, User.Role role, String tokenId, Instant tokenExpiresAt) {
        this(id, role, tokenId, tokenExpiresAt, false);
    }
}
