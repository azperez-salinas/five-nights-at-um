package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/**
 * R38: los mensajes de error devueltos al consumidor no deben exponer
 * informacion interna (stack traces, SQL, rutas internas, claves de
 * configuracion, tokens o detalles de implementacion).
 *
 * Esta clase solo cubre 401/403, que es el UNICO tipo de error donde el body
 * real es observable con MockMvc: lo escriben a mano RestAuthenticationEntryPoint
 * y RestAccessDeniedHandler (ya probados a nivel unitario en
 * RestSecurityHandlersTest), asi que estos tests son un refuerzo end-to-end.
 *
 * Deliberadamente NO cubre otros tipos de error (500 no controlado, 400/404
 * de negocio via ResponseStatusException, 400 por JSON malformado via
 * HttpMessageNotReadableException): se investigo cada uno y en todos los
 * casos MockMvc devuelve un body VACIO en vez del que recibiria un cliente
 * real, porque esas rutas de error dependen del mecanismo estandar de
 * Spring/Servlet (response.sendError() + reenvio interno a /error), que
 * MockMvc no reproduce sin un contenedor real (se verifico forzando un
 * assert fallido para inspeccionar el body real: siempre ""). Probar esos
 * casos de verdad requeriria @SpringBootTest(webEnvironment = RANDOM_PORT) +
 * un cliente HTTP real (TestRestTemplate), lo que se decidio no encarar por
 * ahora: ningun otro test de este proyecto usa ese patron, y el estado
 * (codigo 400/404/409, sin texto filtrado) ya esta cubierto indirectamente
 * por los tests existentes de cada feature (AuthorizationMatrixIntegrationTest,
 * RegistrationAndProfileIntegrationTest, etc.), que solo verifican el status,
 * nunca el contenido del body.
 */
class ErrorResponseLeakageIntegrationTest extends IntegrationTestBase {

    private static final String FAVORITOS = "/api/favorites";
    private static final String LIBRERIA_PROPIA = "/api/users/libreria";

    @Test
    void sinTokenResponde401ConElMensajeUniformeYNadaMas() throws Exception {
        String cuerpo = mvc.perform(get(FAVORITOS))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).isEqualTo("{\"message\":\"No autenticado\"}");
    }

    @Test
    void rolIncorrectoResponde403ConElMensajeUniformeYNadaMas() throws Exception {
        // compradorA no es DUENO: /api/users/libreria es exclusiva del DUENO
        String cuerpo = mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).isEqualTo("{\"message\":\"Acceso denegado\"}");
    }
}
