package librosbuysan.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

/**
 * R37: la autorizacion depende de un token. Estos tests intentan colarse con
 * tokens manipulados o mal formados y comprueban que SIEMPRE terminan en 401
 * y que nunca se ejecuta la operacion protegida.
 *
 * Endpoint objetivo: GET /api/favorites (solo COMPRADOR autenticado).
 */
class TokenAttacksIntegrationTest extends IntegrationTestBase {

    private static final String OBJETIVO = "/api/favorites";

    @Value("${app.jwt.secret}")
    private String secret;

    private SecretKey claveReal() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(UTF_8));
    }

    // ---------- control positivo: si esto falla, el resto no prueba nada ----------

    @Test
    void controlPositivoUnTokenValidDeCompradorAccede() throws Exception {
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk());
    }

    // ---------- ausencia / formato incorrecto ----------

    @Test
    void sinHeaderAuthorizationEs401ConMensajeUniforme() throws Exception {
        mvc.perform(get(OBJETIVO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autenticado"));
    }

    @Test
    void bearerVacioEs401() throws Exception {
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerConBasuraEs401() throws Exception {
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer esto.no.es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void esquemaDistintoDeBearerSeIgnora() throws Exception {
        String token = jwtService.generateToken(compradorA);
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Basic " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenEnLaQueryStringNoSeAcepta() throws Exception {
        String token = jwtService.generateToken(compradorA);
        mvc.perform(get(OBJETIVO).param("token", token).param("access_token", token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- manipulacion del token ----------

    @Test
    void escalarRolEditandoElPayloadInvalidaLaFirma() throws Exception {
        // Un DUENO intenta hacerse pasar por COMPRADOR para entrar a favoritos
        String[] partes = jwtService.generateToken(duenoCentral).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), UTF_8)
                .replace("\"DUENO\"", "\"COMPRADOR\"");
        String falsificado = partes[0] + "." + b64(payload) + "." + partes[2];

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + falsificado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suplantarOtroUsuarioEditandoElSubjectInvalidaLaFirma() throws Exception {
        String[] partes = jwtService.generateToken(compradorB).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), UTF_8)
                .replace("\"sub\":\"" + compradorB.getId() + "\"", "\"sub\":\"" + compradorA.getId() + "\"");
        String falsificado = partes[0] + "." + b64(payload) + "." + partes[2];

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + falsificado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFirmadoConOtraClaveEs401() throws Exception {
        SecretKey otraClave = Keys.hmacShaKeyFor(new byte[48]);
        String token = Jwts.builder()
                .subject(String.valueOf(compradorA.getId()))
                .claim("role", "COMPRADOR")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(otraClave, Jwts.SIG.HS256)
                .compact();

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinFirmaConAlgNoneEs401() throws Exception {
        String token = b64("{\"alg\":\"none\"}") + "."
                + b64("{\"sub\":\"" + compradorA.getId() + "\",\"role\":\"COMPRADOR\"}") + ".";

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoEs401() throws Exception {
        Instant ahora = Instant.now();
        String token = Jwts.builder()
                .subject(String.valueOf(compradorA.getId()))
                .claim("role", "COMPRADOR")
                .issuedAt(Date.from(ahora.minusSeconds(7200)))
                .expiration(Date.from(ahora.minusSeconds(3600)))
                .signWith(claveReal(), Jwts.SIG.HS256)
                .compact();

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBienFirmadoPeroConRolInexistenteEs401() throws Exception {
        String token = Jwts.builder()
                .subject(String.valueOf(compradorA.getId()))
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(claveReal(), Jwts.SIG.HS256)
                .compact();

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBienFirmadoPeroConSubjectNoNumericoEs401() throws Exception {
        String token = Jwts.builder()
                .subject("no-es-un-id")
                .claim("role", "COMPRADOR")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(claveReal(), Jwts.SIG.HS256)
                .compact();

        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- un token invalido en un endpoint publico no lo rompe ----------

    @Test
    void tokenInvalidoEnEndpointPublicoSeTrataComoAnonimo() throws Exception {
        mvc.perform(get("/api/books").header(AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isOk());
    }

    // ---------- respuesta de acceso denegado ----------

    @Test
    void duenoEnRutaDeCompradorRecibe403ConMensajeUniforme() throws Exception {
        mvc.perform(get(OBJETIVO).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"));
    }

    @Test
    void laRespuesta401NoFiltraDetallesDelMotivo() throws Exception {
        // RS31: igual mensaje para "sin token", "token vencido" o "firma invalida"
        String sinToken = mvc.perform(get(OBJETIVO)).andReturn().getResponse().getContentAsString();
        String conBasura = mvc.perform(get(OBJETIVO).header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z"))
                .andReturn().getResponse().getContentAsString();

        assertThat(conBasura).isEqualTo(sinToken);
    }
}
