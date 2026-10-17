package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import librosbuysan.security.AuthorizationAuditLogger;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * RS32/R32: registro de eventos de autenticacion (logins exitosos y fallidos)
 * en el logger AUDIT para proteccion de registros de auditoria.
 */
class AuthenticationAuditIntegrationTest extends IntegrationTestBase {

    private static final String LOGIN = "/api/auth/login";
    private static final String TS_ISO_UTC = ".* ts=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

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
    void unLoginExitosoQuedaRegistradoEnAudit() throws Exception {
        String body = "{\"username\":\"comprador_a\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Request-Id", "audit-login-ok"))
                .andExpect(status().isOk());

        assertThat(unicaLinea()).contains(
                "event=AUTHN outcome=SUCCESS reason=- status=200",
                "user=comprador_a",
                "role=COMPRADOR",
                "method=POST",
                "resource=/api/auth/login",
                "requestId=audit-login-ok"
        ).matches(TS_ISO_UTC);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void unLoginFallidoPorPasswordIncorrectaQuedaRegistradoEnAuditSinFiltrarLaPassword() throws Exception {
        String passwordErronea = "PasswordEquivocada999!";
        String body = "{\"username\":\"comprador_a\",\"password\":\"" + passwordErronea + "\"}";
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Request-Id", "audit-login-wrong-pass"))
                .andExpect(status().isUnauthorized());

        String linea = unicaLinea();
        assertThat(linea).contains(
                "event=AUTHN outcome=FAILED reason=password_incorrecta status=401",
                "user=comprador_a",
                "role=anonymous",
                "method=POST",
                "resource=/api/auth/login",
                "requestId=audit-login-wrong-pass"
        ).matches(TS_ISO_UTC);

        // La contrasena bajo ninguna circunstancia debe aparecer en los registros
        assertThat(linea).doesNotContain(passwordErronea);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void unLoginFallidoPorUsuarioInexistenteQuedaRegistradoEnAudit() throws Exception {
        String body = "{\"username\":\"no_existe\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Request-Id", "audit-login-no-user"))
                .andExpect(status().isUnauthorized());

        assertThat(unicaLinea()).contains(
                "event=AUTHN outcome=FAILED reason=usuario_inexistente status=401",
                "user=no_existe",
                "role=anonymous",
                "method=POST",
                "resource=/api/auth/login",
                "requestId=audit-login-no-user"
        ).matches(TS_ISO_UTC);

        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    private String unicaLinea() {
        assertThat(appender.list).as("Debe emitirse exactamente una linea en AUDIT").hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }
}
