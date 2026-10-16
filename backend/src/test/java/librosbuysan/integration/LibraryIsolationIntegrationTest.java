package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import librosbuysan.book.entity.Book;
import librosbuysan.library.entity.Library;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * RS20: ningun Dueno puede acceder, modificar o eliminar recursos de una
 * libreria ajena, aunque conozca sus identificadores. Complementa a
 * BookOwnershipIntegrationTest, ResourceOwnershipIntegrationTest y
 * LibraryMetricsIntegrationTest recorriendo TODOS los endpoints del Dueno:
 *
 *   GET    /api/books/mine             listar libros propios
 *   PUT    /api/books/{id}             editar libro
 *   DELETE /api/books/{id}             dar de baja libro
 *   POST   /api/books/import           carga masiva CSV
 *   GET    /api/users/libreria         ver libreria propia
 *   DELETE /api/users/libreria/{id}    dar de baja libreria
 *   GET    /api/users/libreria/metricas panel de metricas
 *
 * En cada uno, duenoNorte (Dueno B) intenta operar sobre la libreria de
 * duenoCentral (Dueno A) usando ids reales, por la ruta o colandolos en
 * parametros/cuerpo. Donde el endpoint recibe un id, la respuesta tiene que
 * ser IDENTICA a la de un id inexistente (no revelar que el recurso existe);
 * donde no lo recibe, el id ajeno se ignora y la libreria sale del token.
 */
class LibraryIsolationIntegrationTest extends IntegrationTestBase {

    private static final long ID_INEXISTENTE = 999_999L;

    // ---------- GET /api/books/mine ----------

    @Test
    void listarLibrosPropiosIgnoraParametrosQueApuntanAOtraLibreria() throws Exception {
        mvc.perform(get("/api/books/mine")
                        .param("libreria", central.getId().toString())
                        .param("libraryId", central.getId().toString())
                        .param("libreriaId", central.getId().toString())
                        .header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(libroNorte.getId()))
                .andExpect(jsonPath("$.items[0].nombreLibreria").value("Libreria Norte"));
    }

    // ---------- PUT /api/books/{id} ----------

    @Test
    void editarUnLibroAjenoDaLaMismaRespuestaQueUnoInexistenteYNoLoModifica() throws Exception {
        String body = "{\"titulo\":\"Editado por otro dueno\",\"libreriaId\":" + norte.getId() + "}";

        assertMismaRespuesta(
                put("/api/books/{id}", libroCentral.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(body).header(AUTHORIZATION, bearer(duenoNorte)),
                put("/api/books/{id}", ID_INEXISTENTE).contentType(MediaType.APPLICATION_JSON)
                        .content(body).header(AUTHORIZATION, bearer(duenoNorte)));

        Book libro = bookRepo.findById(libroCentral.getId()).orElseThrow();
        assertThat(libro.getTitulo()).isEqualTo("Don Quijote de la Mancha");
        assertThat(libro.getLibrary().getId()).isEqualTo(central.getId());
    }

    // ---------- DELETE /api/books/{id} ----------

    @Test
    void borrarUnLibroAjenoDaLaMismaRespuestaQueUnoInexistenteYNoLoBorra() throws Exception {
        assertMismaRespuesta(
                delete("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(duenoNorte)),
                delete("/api/books/{id}", ID_INEXISTENTE).header(AUTHORIZATION, bearer(duenoNorte)));

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().isEnabled()).isTrue();
    }

    // ---------- POST /api/books/import ----------

    @Test
    void elCsvSeImportaEnLaLibreriaDelTokenAunqueElRequestMandeOtroLibraryId() throws Exception {
        // El id ajeno viaja como parametro del request y como columna extra del CSV
        String csv = "titulo;autor;isbn;cantidad_paginas;descripcion;anio;libreria_id;libraryId\n"
                + "\"Libro importado\";\"Autor\";9780307474728;100;\"Desc\";2000;"
                + central.getId() + ";" + central.getId() + "\n";

        mvc.perform(multipart("/api/books/import").file(archivo(csv))
                        .param("libraryId", central.getId().toString())
                        .param("libreriaId", central.getId().toString())
                        .param("libreria", central.getId().toString())
                        .header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.imported").value(1));

        assertThat(librosDe(central)).as("la libreria ajena no recibe libros").isEqualTo(1);
        assertThat(librosDe(norte)).as("el libro va a la libreria del token").isEqualTo(2);
    }

    @Test
    void unDuenoSinLibreriaHabilitadaNoPuedeImportarEnUnaAjenaPasandoSuId() throws Exception {
        String csv = "titulo;autor;isbn;cantidad_paginas;descripcion;anio\n"
                + "\"Libro importado\";\"Autor\";9780307474728;100;\"Desc\";2000\n";

        mvc.perform(multipart("/api/books/import").file(archivo(csv))
                        .param("libraryId", central.getId().toString())
                        .header(AUTHORIZATION, bearer(duenoBaja)))
                .andExpect(status().isNotFound());

        assertThat(librosDe(central)).isEqualTo(1);
    }

    // ---------- GET /api/users/libreria ----------

    @Test
    void verLaLibreriaPropiaIgnoraUnIdAjenoEnLosParametros() throws Exception {
        mvc.perform(get("/api/users/libreria")
                        .param("id", central.getId().toString())
                        .param("libraryId", central.getId().toString())
                        .header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(norte.getId()))
                .andExpect(jsonPath("$.nombre").value("Libreria Norte"));
    }

    // ---------- DELETE /api/users/libreria/{id} ----------

    @Test
    void darDeBajaUnaLibreriaAjenaDaLaMismaRespuestaQueUnaInexistente() throws Exception {
        assertMismaRespuesta(
                delete("/api/users/libreria/{id}", central.getId()).header(AUTHORIZATION, bearer(duenoNorte)),
                delete("/api/users/libreria/{id}", ID_INEXISTENTE).header(AUTHORIZATION, bearer(duenoNorte)));

        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(libraryRepo.findById(norte.getId()).orElseThrow().isEnabled())
                .as("tampoco se da de baja la propia por error").isTrue();
    }

    // ---------- GET /api/users/libreria/metricas ----------

    @Test
    void lasMetricasIgnoranUnIdDeLibreriaAjenoEnLosParametros() throws Exception {
        mvc.perform(post("/api/favorites/{bookId}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favorites/{bookId}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/users/libreria/metricas")
                        .param("libraryId", central.getId().toString())
                        .param("libreriaId", central.getId().toString())
                        .param("libreria", central.getId().toString())
                        .header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFavoritos").value(0))
                .andExpect(jsonPath("$.topLibros", hasSize(0)));
    }

    @Test
    void lasMetricasNoTienenUnaVarianteConIdEnLaRuta() throws Exception {
        // Si alguien agregara /metricas/{id}, el deny-by-default lo rechaza (403 con token)
        mvc.perform(get("/api/users/libreria/{id}/metricas", central.getId()).header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/libreria/metricas/{id}", central.getId()).header(AUTHORIZATION, bearer(duenoNorte)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    /** Mismo status y mismo mensaje/cuerpo: la respuesta no revela si el recurso existe. */
    private void assertMismaRespuesta(RequestBuilder ajeno, RequestBuilder inexistente) throws Exception {
        MockHttpServletResponse a = mvc.perform(ajeno).andExpect(status().isNotFound()).andReturn().getResponse();
        MockHttpServletResponse b = mvc.perform(inexistente).andExpect(status().isNotFound()).andReturn().getResponse();
        assertThat(a.getErrorMessage()).isEqualTo(b.getErrorMessage());
        assertThat(a.getContentAsString()).isEqualTo(b.getContentAsString());
    }

    private long librosDe(Library library) {
        return bookRepo.findAll().stream()
                .filter(b -> b.getLibrary().getId().equals(library.getId()))
                .count();
    }

    private static MockMultipartFile archivo(String contenido) {
        return new MockMultipartFile("file", "books.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
    }
}
