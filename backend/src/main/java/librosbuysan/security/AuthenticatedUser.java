package librosbuysan.security;

import java.time.Instant;
import librosbuysan.user.User;

/**
 * Principal que arma JwtAuthenticationFilter a partir de un token ya validado.
 * tokenId (claim jti) y tokenExpiresAt viajan aca para que el logout sepa
 * que token revocar sin tener que volver a parsear el header Authorization.
 */
public record AuthenticatedUser(Long id, User.Role role, String tokenId, Instant tokenExpiresAt) {
}