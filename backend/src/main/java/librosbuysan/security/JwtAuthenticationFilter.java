package librosbuysan.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import librosbuysan.auth.JwtService;
import librosbuysan.auth.TokenRevocationService;
import librosbuysan.user.User;

// Sin @Component a proposito: Spring Boot registraria este filtro dos veces
// (una vez solo en el contenedor servlet, otra via addFilterBefore en
// SecurityConfig). Se instancia a mano en SecurityConfig.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenRevocationService revocationService;

    public JwtAuthenticationFilter(JwtService jwtService, TokenRevocationService revocationService) {
        this.jwtService = jwtService;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                authenticate(token);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Token invalido en request a {}", request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        Claims claims = jwtService.parseAndValidate(token);

        // R3: un token con firma y expiracion validas puede haber sido revocado
        // por un logout. Sin jti no se puede verificar, y sin exp no se sabe
        // hasta cuando guardar la revocacion: en ambos casos se rechaza.
        String tokenId = claims.getId();
        if (tokenId == null || claims.getExpiration() == null || revocationService.isRevoked(tokenId)) {
            log.warn("Token revocado o incompleto (sin jti/exp) rechazado");
            return;
        }

        Long userId = Long.valueOf(claims.getSubject());
        User.Role role = User.Role.valueOf(claims.get(JwtService.ROLE_CLAIM, String.class));

        AuthenticatedUser principal = new AuthenticatedUser(userId, role, tokenId, claims.getExpiration().toInstant());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}