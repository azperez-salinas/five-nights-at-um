package librosbuysan.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Brechas de autorizacion DETECTADAS pero todavia no corregidas en el codigo
 * de produccion. Cada test describe el comportamiento CORRECTO esperado; estan
 * en @Disabled para no romper la build mientras el equipo decide como
 * resolverlas. Cuando se corrija una, se saca el @Disabled y el test pasa a
 * ser su guardia de regresion.
 *
 * GAP-1 (token de cuenta deshabilitada) se resolvio en R22 y su test se movio
 * a DisabledAccountIntegrationTest.
 */
class KnownAuthorizationGapsIntegrationTest extends IntegrationTestBase {

    @Disabled("GAP-2: SecurityConfig usa GET /api/books/** (comodin). Cualquier GET futuro bajo "
            + "/api/books/ nacera publico sin que nadie lo decida. Fix: listar rutas exactas "
            + "(/api/books, /api/books/search, /api/books/{id}).")
    @Test
    void rutaNoDeclaradaBajoApiBooksNoDebeSerPublica() throws Exception {
        mvc.perform(get("/api/books/1/reservas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books/1/reservas").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden());
    }
}
