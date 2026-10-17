package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.UUID;
import librosbuysan.book.service.BookService;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * RS11/R11: la identidad del consumidor que origino la operacion viaja con
 * ella por toda la cadena filtro JWT -> controller -> service -> repositorio,
 * incluidos los logs (MDC: requestId, userId, role).
 *
 * Que cada service decida con la identidad del token y no con ids enviados
 * por el cliente ya lo prueban LibraryIsolationIntegrationTest (RS20),
 * ResourceOwnershipIntegrationTest y BookOwnershipIntegrationTest: un dueno
 * nunca opera sobre la libreria de otro aunque mande sus ids. Aca se prueba
 * la parte nueva: el requestId y que la identidad del principal (y no un
 * header del cliente) llega hasta el log del service, sin filtrarse entre
 * requests.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestIdentityPropagationIntegrationTest extends IntegrationTestBase {

    private static final String REQUEST_ID = "X-Request-Id";

    private final Logger bookServiceLogger = (Logger) LoggerFactory.getLogger(BookService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capturarLogsDelService() {
        appender.start();
        bookServiceLogger.addAppender(appender);
    }

    @AfterEach
    void soltarAppender() {
        bookServiceLogger.detachAppender(appender);
    }

    @Test
    void todaRespuestaLlevaUnRequestIdGeneradoSiElClienteNoManda() throws Exception {
        String publico = mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(REQUEST_ID);
        String sinToken = mvc.perform(get("/api/books/mine"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(REQUEST_ID);

        assertThat(UUID.fromString(publico)).isNotNull();
        assertThat(UUID.fromString(sinToken)).isNotNull();
        assertThat(publico).isNotEqualTo(sinToken);
    }

    @Test
    void unRequestIdEntranteValidoSePropaga() throws Exception {
        mvc.perform(get("/api/books").header(REQUEST_ID, "front-1234_abc.9"))
                .andExpect(status().isOk())
                .andExpect(header().string(REQUEST_ID, "front-1234_abc.9"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"con espacios", "<script>", "abc;user=1", "ñandú", ""})
    void unRequestIdEntranteMaliciosoSeReemplaza(String malicioso) throws Exception {
        String devuelto = mvc.perform(get("/api/books").header(REQUEST_ID, malicioso))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(REQUEST_ID);

        assertThat(devuelto).isNotEqualTo(malicioso);
        assertThat(UUID.fromString(devuelto)).isNotNull();
    }

    // Con CR/LF el request ni siquiera llega al filtro: el StrictHttpFirewall
    // de Spring Security lo corta con 400. Igual se verifica que el valor
    // nunca vuelva en la respuesta (tampoco lo aceptaria sanitizeRequestId).
    @ParameterizedTest
    @ValueSource(strings = {"abc\r\nFAKE LOG LINE", "abc\nuser=1"})
    void unRequestIdConSaltoDeLineaSeRechazaYNoSeDevuelve(String malicioso) throws Exception {
        String devuelto = mvc.perform(get("/api/books").header(REQUEST_ID, malicioso))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getHeader(REQUEST_ID);

        assertThat(devuelto).isNotEqualTo(malicioso);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void unRequestIdEntranteDemasiadoLargoSeReemplaza() throws Exception {
        String largo = "a".repeat(65);
        String devuelto = mvc.perform(get("/api/books").header(REQUEST_ID, largo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(REQUEST_ID);

        assertThat(UUID.fromString(devuelto)).isNotNull();
    }

    @Test
    void elLogDelServiceLlevaLaIdentidadDelTokenYNoLaQueMandaElCliente(CapturedOutput output) throws Exception {
        // El cliente intenta hacerse pasar por otro usuario con headers propios:
        // la identidad sale del principal del JWT, no de estos valores.
        mvc.perform(delete("/api/books/{id}", libroCentral.getId())
                        .header(AUTHORIZATION, bearer(duenoCentral))
                        .header(REQUEST_ID, "rs11-cadena")
                        .header("X-User-Id", duenoNorte.getId().toString())
                        .header("X-Role", "ADMIN"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(REQUEST_ID, "rs11-cadena"));

        ILoggingEvent baja = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("Libro dado de baja"))
                .findFirst().orElseThrow();
        assertThat(baja.getMDCPropertyMap()).containsAllEntriesOf(Map.of(
                "requestId", "rs11-cadena",
                "userId", duenoCentral.getId().toString(),
                "role", "DUENO"));

        // logging.pattern.level: la linea de log impresa lleva la identidad
        assertThat(output.getOut())
                .contains("[req=rs11-cadena user=" + duenoCentral.getId() + " role=DUENO]");
    }

    @Test
    void unRequestSinTokenQuedaRegistradoComoAnonimo() throws Exception {
        // Un catalogo con pagina invalida es publico y loguea en BookService
        mvc.perform(get("/api/books").param("page", "0").header(REQUEST_ID, "rs11-anonimo"))
                .andExpect(status().isBadRequest());

        ILoggingEvent rechazo = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("Solicitud de catalogo invalida"))
                .findFirst().orElseThrow();
        assertThat(rechazo.getMDCPropertyMap()).containsAllEntriesOf(Map.of(
                "requestId", "rs11-anonimo",
                "userId", "anonymous",
                "role", "anonymous"));
    }

    @Test
    void elMdcQuedaVacioDespuesDeCadaRequestYNoSeFiltraAlSiguiente() throws Exception {
        // MockMvc ejecuta los filtros en el mismo hilo del test, igual que un
        // hilo del pool de Tomcat que atiende requests sucesivos.
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        mvc.perform(delete("/api/books/{id}", libroNorte.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden());
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        mvc.perform(get("/api/books").param("page", "0")).andExpect(status().isBadRequest());
        ILoggingEvent ultimo = appender.list.get(appender.list.size() - 1);
        assertThat(ultimo.getMDCPropertyMap()).containsEntry("userId", "anonymous");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
