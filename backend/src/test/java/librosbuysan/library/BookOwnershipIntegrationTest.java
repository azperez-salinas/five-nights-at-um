package librosbuysan.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * R18/F18: un dueno gestiona (lista/edita/borra) exclusivamente los libros de
 * SU propia libreria (RS20/RS24/R6-IDOR). La politica por ROL (que solo
 * DUENO puede llamar a estos endpoints) ya esta cubierta en
 * AuthorizationMatrixIntegrationTest; aca se prueba la pertenencia por
 * TENANT, que es lo que decide BookService, no SecurityConfig.
 *
 * Escenario (ver IntegrationTestBase): duenoCentral es dueno de "central"
 * (habilitada, con libroCentral); duenoNorte es dueno de "norte" (habilitada,
 * con libroNorte); duenoBaja es dueno de "baja" (DESHABILITADA, con
 * libroBaja). libroCentral/libroNorte/libroBaja son el MISMO titulo/ISBN,
 * una copia por libreria.
 */
class BookOwnershipIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------
    // GET /api/books/mine
    // ------------------------------------------------------------------

    @Test
    void elDuenoSoloVeLosLibrosDeSuPropiaLibreria() throws Exception {
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(libroCentral.getId()))
                .andExpect(jsonPath("$.items[0].nombreLibreria").value("Libreria Central"));
    }

    @Test
    void unDuenoConLibreriaDeshabilitadaNoPuedeListarSusLibros() throws Exception {
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, bearer(duenoBaja)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // PUT /api/books/{id}
    // ------------------------------------------------------------------

    @Test
    void elDuenoPuedeEditarUnLibroDeSuPropiaLibreria() throws Exception {
        String body = "{\"titulo\":\"Don Quijote (edicion anotada)\"}";

        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Don Quijote (edicion anotada)"))
                // campos no enviados no cambian
                .andExpect(jsonPath("$.autor").value("Miguel de Cervantes"));

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().getTitulo())
                .isEqualTo("Don Quijote (edicion anotada)");
    }

    @Test
    void unDuenoNoPuedeEditarUnLibroDeOtraLibreriaYRecibeElMismoErrorQueSiNoExistiera() throws Exception {
        String body = "{\"titulo\":\"Intento ajeno\"}";

        String ajeno = mvc.perform(put("/api/books/{id}", libroNorte.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();
        String inexistente = mvc.perform(put("/api/books/{id}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();

        assertThat(ajeno).isEqualTo(inexistente);
        // El libro ajeno no se modifico
        assertThat(bookRepo.findById(libroNorte.getId()).orElseThrow().getTitulo())
                .isEqualTo("Don Quijote de la Mancha");
    }

    @Test
    void elPutIgnoraLibreriaIdAunqueElClienteLoMande() throws Exception {
        // RS36: libreriaId no es un campo de UpdateBookRequest, asi que
        // aunque el JSON lo traiga, Jackson lo descarta sin tocar nada.
        String body = "{\"titulo\":\"Intento de mover libro\",\"libreriaId\":" + norte.getId() + "}";

        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreLibreria").value("Libreria Central"));

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().getLibrary().getId())
                .isEqualTo(central.getId());
    }

    @Test
    void editarConIsbnInvalidoEs400() throws Exception {
        String body = "{\"isbn\":\"no-es-un-isbn-valido\"}";

        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isBadRequest());

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().getIsbn())
                .isEqualTo("9789900000013");
    }

    @Test
    void unDuenoConLibreriaDeshabilitadaNoPuedeEditarNiSusPropiosLibros() throws Exception {
        String body = "{\"titulo\":\"Intento\"}";

        mvc.perform(put("/api/books/{id}", libroBaja.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(duenoBaja)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // DELETE /api/books/{id}
    // ------------------------------------------------------------------

    @Test
    void elDuenoPuedeDarDeBajaUnLibroDeSuPropiaLibreriaYDejaDeVerseEnCatalogoBusquedaYDetalle() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().isEnabled()).isFalse();

        // RS34/R18: deja de verse en catalogo, busqueda, detalle y en el propio "mine"
        mvc.perform(get("/api/books/{id}", libroCentral.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/books/search").param("q", "quijote").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1)); // antes eran 2 (central + norte)
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unDuenoNoPuedeBorrarUnLibroDeOtraLibreriaYRecibeElMismoErrorQueSiNoExistiera() throws Exception {
        String ajeno = mvc.perform(delete("/api/books/{id}", libroNorte.getId())
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();
        String inexistente = mvc.perform(delete("/api/books/{id}", 999_999L)
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();

        assertThat(ajeno).isEqualTo(inexistente);
        assertThat(bookRepo.findById(libroNorte.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void darDeBajaUnLibroYaDadoDeBajaEs404() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unDuenoConLibreriaDeshabilitadaNoPuedeBorrarNiSusPropiosLibros() throws Exception {
        mvc.perform(delete("/api/books/{id}", libroBaja.getId()).header(AUTHORIZATION, bearer(duenoBaja)))
                .andExpect(status().isNotFound());

        assertThat(bookRepo.findById(libroBaja.getId()).orElseThrow().isEnabled()).isTrue();
    }
}
