package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import librosbuysan.security.AuthorizationAuditLogger;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * RS23/R23: las decisiones de autorizacion quedan registradas en el logger
 * AUDIT con consumidor, operacion, recurso, resultado y fecha/hora, sin
 * secretos. Cubre los cuatro puntos de decision:
 *
 *   401 sin token / token invalido   -> RestAuthenticationEntryPoint
 *   403 rol sin permiso               -> RestAccessDeniedHandler
 *   404 recurso ajeno (RS20/R6)       -> AuthorizationAuditInterceptor (DENIED)
 *   2xx operacion propia permitida    -> AuthorizationAuditInterceptor (ALLOWED)
 */
class AuthorizationAuditIntegrationTest extends IntegrationTestBase {

    private static final String TS_ISO_UTC = ".* ts=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    @Autowired private AuthorizationAuditLogger auditLogger;

    private final Logger auditLogback = (Logger) LoggerFactory.getLogger(AuthorizationAuditLogger.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capturarAudit() {
        appender.start();
        auditLogback.addAppender(appender);
    }

    @AfterEach
    void soltarAppender() {
        auditLogback.detachAppender(appender);
    }

    @Test
    void unRequestSinTokenQuedaRegistradoComoDenegadoYAnonimo() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header("X-Request-Id", "rs23-401"))
                .andExpect(status().isUnauthorized());

        assertThat(unicaLinea()).contains("event=AUTHZ outcome=DENIED reason=NO_TOKEN status=401",
                        "user=anonymous role=anonymous", "method=DELETE",
                        "resource=/api/books/" + libroCentral.getId(), "requestId=rs23-401")
                .matches(TS_ISO_UTC);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void unTokenInvalidoQuedaRegistradoSinQueElTokenAparezcaEnElLog() throws Exception {
        String tokenAdulterado = jwtService.generateToken(duenoCentral) + "x";

        mvc.perform(delete("/api/books/{id}", libroCentral.getId())
                        .header(AUTHORIZATION, "Bearer " + tokenAdulterado))
                .andExpect(status().isUnauthorized());

        assertThat(unicaLinea()).contains("outcome=DENIED reason=INVALID_TOKEN status=401",
                        "user=anonymous role=anonymous")
                .doesNotContain(tokenAdulterado).doesNotContain("Bearer");
    }

    @Test
    void unRolSinPermisoQuedaRegistradoConElUsuarioYSuRol() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden());

        assertThat(unicaLinea()).contains("outcome=DENIED reason=FORBIDDEN_ROLE status=403",
                "user=" + compradorA.getId() + " role=COMPRADOR", "method=DELETE",
                "resource=/api/books/" + libroCentral.getId());
    }

    @Test
    void unDuenoQueBorraOEditaUnLibroAjenoQuedaRegistradoComoDenegado() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoNorte))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Ajeno\"}"))
                .andExpect(status().isNotFound());

        assertThat(lineas()).hasSize(2).allSatisfy(linea -> assertThat(linea)
                .contains("outcome=DENIED reason=NOT_OWNER_OR_NOT_FOUND status=404",
                        "user=" + duenoNorte.getId() + " role=DUENO",
                        "resource=/api/books/" + libroCentral.getId()));
        assertThat(lineas().get(0)).contains("method=DELETE");
        assertThat(lineas().get(1)).contains("method=PUT");
    }

    @Test
    void editarYBorrarUnLibroPropioQuedaRegistradoComoPermitido() throws Exception {
        mvc.perform(put("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Nuevo titulo\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());

        assertThat(lineas()).hasSize(2);
        assertThat(lineas().get(0)).contains("outcome=ALLOWED", "status=200", "method=PUT",
                "user=" + duenoCentral.getId() + " role=DUENO", "resource=/api/books/" + libroCentral.getId());
        assertThat(lineas().get(1)).contains("outcome=ALLOWED", "status=204", "method=DELETE");
        assertThat(appender.list).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.INFO));
    }

    @Test
    void losGetPublicosYLosGetProtegidosPermitidosNoLlenanElLog() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isOk());
        mvc.perform(get("/api/books/{id}", libroCentral.getId())).andExpect(status().isOk());
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, bearer(duenoCentral))).andExpect(status().isOk());
        // Login: autenticacion, no una decision de autorizacion
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comprador_a\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        assertThat(appender.list).isEmpty();
    }

    @Test
    void ningunTokenNiDatoSensibleApareceEnElLog() throws Exception {
        String token = jwtService.generateToken(duenoCentral);

        mvc.perform(put("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Titulo secreto del body\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/books/{id}", libroNorte.getId()).header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/favorites").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(lineas()).hasSize(3).allSatisfy(linea -> assertThat(linea)
                .doesNotContain(token).doesNotContain("Bearer").doesNotContain("Authorization")
                .doesNotContain("Titulo secreto").doesNotContain(duenoCentral.getEmail()));
    }

    @Test
    void unPathConSaltosDeLineaNoRompeLaLineaDeAuditoria() {
        // El firewall de Spring Security ya rechaza CR/LF en la URL antes de
        // llegar a un punto de decision; el logger igual se defiende solo.
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE",
                "/api/books/1\r\nevent=AUTHZ outcome=ALLOWED user=1\t" + "x".repeat(500));

        auditLogger.denied(request, "FORBIDDEN_ROLE", 403);

        String linea = unicaLinea();
        assertThat(linea).doesNotContain("\r").doesNotContain("\n").doesNotContain("\t")
                .contains("resource=/api/books/1__event_AUTHZ_outcome_ALLOWED_user_1_x")
                .matches(TS_ISO_UTC);
        assertThat(linea.split("outcome=")).as("una sola decision por linea").hasSize(2);
        assertThat(linea.length()).isLessThan(400);
    }

    private List<String> lineas() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private String unicaLinea() {
        assertThat(appender.list).hasSize(1);
        return lineas().get(0);
    }
}
