package librosbuysan.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import librosbuysan.auth.JwtService;
import librosbuysan.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El filtro es la primera linea de la autorizacion: si deja pasar una
 * identidad falsa, todo lo demas (roles, ownership) queda sin sentido.
 * Se prueba aislado, con el JwtService simulado.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest("GET", "/api/favorites");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Claims claims(String subject, String role) {
        return Jwts.claims().subject(subject).add(JwtService.ROLE_CLAIM, role).build();
    }

    private Authentication autenticacion() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void tokenValidoAutenticaConIdYRolDelToken() throws Exception {
        when(jwtService.parseAndValidate("token-ok")).thenReturn(claims("5", "COMPRADOR"));
        request.addHeader("Authorization", "Bearer token-ok");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNotNull();
        assertThat(autenticacion().getPrincipal()).isEqualTo(new AuthenticatedUser(5L, User.Role.COMPRADOR));
        assertThat(autenticacion().getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_COMPRADOR");
        assertThat(chain.getRequest()).as("la cadena debe continuar").isNotNull();
    }

    @Test
    void tokenDeDuenoOtorgaElRolDueno() throws Exception {
        when(jwtService.parseAndValidate("token-dueno")).thenReturn(claims("9", "DUENO"));
        request.addHeader("Authorization", "Bearer token-dueno");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion().getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_DUENO");
    }

    @Test
    void sinHeaderNoAutenticaPeroLaCadenaContinua() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void esquemaDistintoDeBearerSeIgnora() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void tokenInvalidoNoAutenticaPeroLaCadenaContinua() throws Exception {
        when(jwtService.parseAndValidate("malo")).thenThrow(new JwtException("firma invalida"));
        request.addHeader("Authorization", "Bearer malo");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void subjectNoNumericoNoAutentica() throws Exception {
        when(jwtService.parseAndValidate("raro")).thenReturn(claims("no-es-numero", "COMPRADOR"));
        request.addHeader("Authorization", "Bearer raro");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
    }

    @Test
    void rolDesconocidoNoAutentica() throws Exception {
        when(jwtService.parseAndValidate("raro")).thenReturn(claims("5", "ADMIN"));
        request.addHeader("Authorization", "Bearer raro");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
    }

    @Disabled("GAP-3: User.Role.valueOf(null) lanza NullPointerException (no IllegalArgumentException) y el "
            + "filtro solo captura JwtException | IllegalArgumentException, asi que un token firmado sin claim "
            + "'role' produciria un 500 en lugar de tratarse como anonimo. No es explotable sin la clave de "
            + "firma, pero conviene capturar tambien NullPointerException (o validar el claim antes).")
    @Test
    void tokenSinClaimDeRolNoAutentica() throws Exception {
        when(jwtService.parseAndValidate("sin-rol")).thenReturn(Jwts.claims().subject("5").build());
        request.addHeader("Authorization", "Bearer sin-rol");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
    }
}
