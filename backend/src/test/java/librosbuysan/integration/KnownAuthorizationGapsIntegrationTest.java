package librosbuysan.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Brechas de autorizacion DETECTADAS pero todavia no corregidas en el codigo
 * de produccion. Cada test describe el comportamiento CORRECTO esperado; estan
 * en @Disabled para no romper la build mientras el equipo decide como
 * resolverlas. Cuando se corrija una, se saca el @Disabled y el test pasa a
 * ser su guardia de regresion.
 */
class KnownAuthorizationGapsIntegrationTest extends IntegrationTestBase {

    @Disabled("GAP-1: JwtAuthenticationFilter no consulta User.enabled; un token de una cuenta "
            + "deshabilitada sigue valido hasta que expira (60 min para COMPRADOR). "
            + "Fix: validar enabled en el filtro, o acortar/revocar tokens.")
    @Test
    void tokenDeUsuarioDeshabilitadoNoDebeAccederAFavoritos() throws Exception {
        String token = bearer(compradorA);

        ReflectionTestUtils.setField(compradorA, "enabled", false);
        userRepo.saveAndFlush(compradorA);

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/favorites/{id}", libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

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
