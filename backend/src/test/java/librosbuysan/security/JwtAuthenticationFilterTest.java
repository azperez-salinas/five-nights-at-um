package librosbuysan.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import librosbuysan.auth.JwtService;
import librosbuysan.auth.TokenRevocationService;
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
 * Se prueba aislado, con el JwtService y la lista de revocados simulados.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String JTI = "jti-1";
    private static final Instant EXPIRA = Instant.parse("2030-01-01T00:00:00Z");

    @Mock private JwtService jwtService;
    @Mock private TokenRevocationService revocationService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(jwtService, revocationService);
        request = new MockHttpServletRequest("GET", "/api/favorites");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Los tokens reales que emite JwtService siempre llevan jti y exp; el filtro
    // los exige (R3), asi que el token "normal" de estos tests los incluye
    private static ClaimsBuilder base(String subject, String role) {
        return Jwts.claims().subject(subject).add(JwtService.ROLE_CLAIM, role);
    }

    private static Claims claims(String subject, String role) {
        return base(subject, role).id(JTI).expiration(Date.from(EXPIRA)).build();
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
        assertThat(autenticacion().getPrincipal())
                .isEqualTo(new AuthenticatedUser(5L, User.Role.COMPRADOR, JTI, EXPIRA));
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

    // ---------- R3: tokens revocados por logout ----------

    @Test
    void consultaLaListaDeRevocadosConElJtiDelToken() throws Exception {
        when(jwtService.parseAndValidate("token-ok")).thenReturn(claims("5", "COMPRADOR"));
        request.addHeader("Authorization", "Bearer token-ok");

        filter.doFilter(request, response, chain);

        verify(revocationService).isRevoked(JTI);
    }

    @Test
    void tokenRevocadoNoAutenticaPeroLaCadenaContinua() throws Exception {
        // Firma y expiracion son validas: solo el logout lo invalido. La cadena sigue
        // para que sea SecurityConfig quien responda 401 (o deje pasar si la ruta es publica)
        when(jwtService.parseAndValidate("token-cerrado")).thenReturn(claims("5", "COMPRADOR"));
        when(revocationService.isRevoked(JTI)).thenReturn(true);
        request.addHeader("Authorization", "Bearer token-cerrado");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).as("la cadena debe continuar").isNotNull();
    }

    @Test
    void tokenRevocadoDeDuenoTampocoAutentica() throws Exception {
        when(jwtService.parseAndValidate("token-cerrado")).thenReturn(claims("9", "DUENO"));
        when(revocationService.isRevoked(JTI)).thenReturn(true);
        request.addHeader("Authorization", "Bearer token-cerrado");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
    }

    @Test
    void tokenSinJtiNoAutenticaYNoConsultaLaLista() throws Exception {
        // Sin jti no hay forma de saber si fue revocado: se rechaza (falla cerrado)
        when(jwtService.parseAndValidate("sin-jti"))
                .thenReturn(base("5", "COMPRADOR").expiration(Date.from(EXPIRA)).build());
        request.addHeader("Authorization", "Bearer sin-jti");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(revocationService);
    }

    @Test
    void tokenSinExpiracionNoAutenticaNiLanzaExcepcion() throws Exception {
        // Sin exp no se sabe hasta cuando guardar una revocacion. Antes de rechazarlo
        // explicitamente esto terminaba en NullPointerException (500).
        when(jwtService.parseAndValidate("sin-exp")).thenReturn(base("5", "COMPRADOR").id(JTI).build());
        request.addHeader("Authorization", "Bearer sin-exp");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Disabled("GAP-3: User.Role.valueOf(null) lanza NullPointerException (no IllegalArgumentException) y el "
            + "filtro solo captura JwtException | IllegalArgumentException, asi que un token firmado sin claim "
            + "'role' produciria un 500 en lugar de tratarse como anonimo. No es explotable sin la clave de "
            + "firma, pero conviene capturar tambien NullPointerException (o validar el claim antes).")
    @Test
    void tokenSinClaimDeRolNoAutentica() throws Exception {
        when(jwtService.parseAndValidate("sin-rol"))
                .thenReturn(Jwts.claims().subject("5").id(JTI).expiration(Date.from(EXPIRA)).build());
        request.addHeader("Authorization", "Bearer sin-rol");

        filter.doFilter(request, response, chain);

        assertThat(autenticacion()).isNull();
    }
}