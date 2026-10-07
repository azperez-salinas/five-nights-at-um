package librosbuysan.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import librosbuysan.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    // 32 bytes exactos (minimo permitido), en base64. Solo para tests.
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTRO_SECRET = Base64.getEncoder()
            .encodeToString("ABCDEF9876543210ABCDEF9876543210".getBytes());

    private final JwtService jwtService = new JwtService(SECRET, 60, 15);

    private static User usuario(User.Role role, long id) {
        User user = role == User.Role.DUENO
                ? User.dueno("pepe", "pepe@test.local", "hash")
                : new User("pepe", "pepe@test.local", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ---------- generacion ----------

    @Test
    void elTokenLlevaIdComoSubjectYElRolComoClaim() {
        String token = jwtService.generateToken(usuario(User.Role.DUENO, 7L));

        Claims claims = jwtService.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get(JwtService.ROLE_CLAIM, String.class)).isEqualTo("DUENO");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void cadaTokenTieneUnJtiDistinto() {
        User user = usuario(User.Role.COMPRADOR, 1L);

        String id1 = jwtService.parseAndValidate(jwtService.generateToken(user)).getId();
        String id2 = jwtService.parseAndValidate(jwtService.generateToken(user)).getId();

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void elTokenNoIncluyeDatosSensiblesDelUsuario() {
        String token = jwtService.generateToken(usuario(User.Role.COMPRADOR, 1L));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(payload).doesNotContain("hash").doesNotContain("pepe@test.local");
    }

    @Test
    void laExpiracionDelCompradorEs60MinutosYLaDelDuenoEs15() {
        Claims comprador = jwtService.parseAndValidate(jwtService.generateToken(usuario(User.Role.COMPRADOR, 1L)));
        Claims dueno = jwtService.parseAndValidate(jwtService.generateToken(usuario(User.Role.DUENO, 2L)));

        assertThat(comprador.getExpiration().getTime() - comprador.getIssuedAt().getTime()).isEqualTo(60 * 60 * 1000L);
        assertThat(dueno.getExpiration().getTime() - dueno.getIssuedAt().getTime()).isEqualTo(15 * 60 * 1000L);
    }

    @Test
    void getExpirationSecondsDependeDelRol() {
        assertThat(jwtService.getExpirationSeconds(User.Role.COMPRADOR)).isEqualTo(3600);
        assertThat(jwtService.getExpirationSeconds(User.Role.DUENO)).isEqualTo(900);
    }

    // ---------- validacion ----------

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService(OTRO_SECRET, 60, 15);
        String token = otro.generateToken(usuario(User.Role.COMPRADOR, 1L));

        assertThatThrownBy(() -> jwtService.parseAndValidate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenExpirado() {
        String vencido = Jwts.builder()
                .subject("1")
                .claim(JwtService.ROLE_CLAIM, "COMPRADOR")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(vencido)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rechazaTextoQueNoEsUnJwt() {
        assertThatThrownBy(() -> jwtService.parseAndValidate("no-es-un-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnTokenSinFirma() {
        String sinFirma = Jwts.builder().subject("1").claim(JwtService.ROLE_CLAIM, "COMPRADOR").compact();

        assertThatThrownBy(() -> jwtService.parseAndValidate(sinFirma)).isInstanceOf(JwtException.class);
    }

    // ---------- configuracion ----------

    @Test
    void elConstructorRechazaSecretoNuloOEnBlanco() {
        assertThatThrownBy(() -> new JwtService(null, 60, 15)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("   ", 60, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elConstructorRechazaSecretoDeMenosDe32BytesSinFiltrarloEnElMensaje() {
        String corto = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

        assertThatThrownBy(() -> new JwtService(corto, 60, 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(corto));
    }

    @Test
    void elConstructorRechazaSecretoQueNoEsBase64SinFiltrarloEnElMensaje() {
        String invalido = "###no-es-base64-valido###";

        assertThatThrownBy(() -> new JwtService(invalido, 60, 15))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalido));
    }

    @Test
    void elConstructorRechazaExpiracionesNoPositivas() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0, 15)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, 60, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(SECRET, -5, 15)).isInstanceOf(IllegalStateException.class);
    }
}
