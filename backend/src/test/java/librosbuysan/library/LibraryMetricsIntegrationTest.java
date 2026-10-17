package librosbuysan.library;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

/**
 * R13/F13: panel de metricas del dueno. RS20 es el foco de este archivo: un
 * favorito puede recaer sobre libros de varias librerias (el catalogo de R7
 * no esta filtrado por libreria), asi que la agregacion tiene que filtrar
 * por la libreria DEL LIBRO de cada favorito, nunca por nada global. La
 * politica por ROL (solo DUENO puede llamar a este endpoint) ya esta cubierta
 * en AuthorizationMatrixIntegrationTest; aca se prueba la pertenencia por
 * TENANT, que es lo que decide LibraryService/FavoriteRepo, no SecurityConfig.
 *
 * Escenario (ver IntegrationTestBase): libroCentral/libroNorte/libroBaja son
 * el MISMO titulo/ISBN, una copia por libreria — asi se prueba que el filtro
 * es por libreria y no por contenido del libro.
 */
class LibraryMetricsIntegrationTest extends IntegrationTestBase {

    @Test
    void elDuenoSoloVeLosFavoritosDeLosLibrosDeSuPropiaLibreria() throws Exception {
        // compradorA favoritea el libro de CENTRAL y el de NORTE (mismo
        // titulo/ISBN, libros distintos); compradorB favoritea solo el de CENTRAL.
        mvc.perform(post("/api/favorites/{bookId}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favorites/{bookId}", libroNorte.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favorites/{bookId}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isCreated());

        // RS20: duenoCentral NO ve el favorito de NORTE, aunque sea el mismo titulo/ISBN
        mvc.perform(get("/api/users/libreria/metricas").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFavoritos").value(2))
                .andExpect(jsonPath("$.topLibros", hasSize(1)))
                .andExpect(jsonPath("$.topLibros[0].bookId").value(libroCentral.getId()))
                .andExpect(jsonPath("$.topLibros[0].cantidadFavoritos").value(2))
                // RS2/RS7: nunca se expone que usuario favoriteo el libro
                .andExpect(jsonPath("$.topLibros[0].userId").doesNotExist())
                .andExpect(jsonPath("$.topLibros[0].usuario").doesNotExist());

        // RS20/RS5: duenoNorte solo ve el suyo, nunca el total de la plataforma
        mvc.perform(get("/api/users/libreria/metricas").header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFavoritos").value(1))
                .andExpect(jsonPath("$.topLibros", hasSize(1)))
                .andExpect(jsonPath("$.topLibros[0].bookId").value(libroNorte.getId()))
                .andExpect(jsonPath("$.topLibros[0].cantidadFavoritos").value(1));
    }

    @Test
    void sinFavoritosElTotalEsCeroYElRankingEstaVacio() throws Exception {
        mvc.perform(get("/api/users/libreria/metricas").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFavoritos").value(0))
                .andExpect(jsonPath("$.topLibros", hasSize(0)));
    }

    @Test
    void unDuenoConLibreriaDeshabilitadaNoPuedeVerSusMetricas() throws Exception {
        // Mismo criterio 404 que GET /api/users/libreria y GET /api/books/mine
        mvc.perform(get("/api/users/libreria/metricas").header(AUTHORIZATION, bearer(duenoBaja)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unLibroDadoDeBajaDejaDeContarEnLasMetricasDeSuPropiaLibreria() throws Exception {
        mvc.perform(post("/api/favorites/{bookId}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());

        // RS34: baja logica del libro (R18), despues de que ya tenia el favorito
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/users/libreria/metricas").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFavoritos").value(0))
                .andExpect(jsonPath("$.topLibros", hasSize(0)));
    }
}
