package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.favorite.Favorite;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * R15/F15: un comprador solo accede a recursos de SU cuenta, salvo los
 * recursos explicitamente publicos (el catalogo).
 *
 * Recursos publicos:  catalogo, busqueda y detalle de libros, sin importar la
 *                     libreria a la que pertenezcan.
 * Recursos privados:  favoritos y perfil (por cuenta), y la libreria (por dueno).
 *
 * Escenario (ver IntegrationTestBase): compradorA y compradorB, librerias
 * central y norte habilitadas, baja deshabilitada.
 */
class ResourceOwnershipIntegrationTest extends IntegrationTestBase {

    // ------------------------------------------------------------------
    // Recursos PUBLICOS: el comprador los ve sin importar la libreria
    // ------------------------------------------------------------------

    @Test
    void compradorVeElCatalogoDeTodasLasLibreriasHabilitadas() throws Exception {
        mvc.perform(get("/api/books").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].nombreLibreria",
                        containsInAnyOrder("Libreria Central", "Libreria Norte")));
    }

    @Test
    void compradorPuedeVerElDetalleDeUnLibroDeCualquierLibreria() throws Exception {
        mvc.perform(get("/api/books/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreLibreria").value("Libreria Central"));
        mvc.perform(get("/api/books/{id}", libroNorte.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreLibreria").value("Libreria Norte"));
    }

    @Test
    void elCatalogoTambienEsPublicoParaUnVisitanteSinSesion() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isOk());
        mvc.perform(get("/api/books/{id}", libroNorte.getId())).andExpect(status().isOk());
    }

    @Test
    void laBusquedaDevuelveCoincidenciasDeVariasLibrerias() throws Exception {
        mvc.perform(get("/api/books/search").param("q", "quijote").header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void elDetalleDeUnLibroDeUnaLibreriaDeshabilitadaNoEsVisibleParaNadie() throws Exception {
        mvc.perform(get("/api/books/{id}", libroBaja.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Recursos PRIVADOS: favoritos
    // ------------------------------------------------------------------

    @Test
    void compradorPuedeFavoritearUnLibroPublicoDeCualquierLibreria() throws Exception {
        mvc.perform(post("/api/favorites/{id}", libroNorte.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());
    }

    @Test
    void elFavoritoSeCreaSiempreEnLaCuentaDelTokenYNoEnOtra() throws Exception {
        mvc.perform(post("/api/favorites/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());

        assertThat(favoriteRepo.count()).isEqualTo(1);
        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorA.getId(), libroCentral.getId())).isTrue();
    }

    @Test
    void unBodyConOtroUserIdNoPermiteCrearFavoritosEnLaCuentaAjena() throws Exception {
        String body = "{\"userId\":" + compradorB.getId() + ",\"user\":{\"id\":" + compradorB.getId() + "}}";

        mvc.perform(post("/api/favorites/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());

        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorA.getId(), libroCentral.getId())).isTrue();
        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorB.getId(), libroCentral.getId())).isFalse();
    }

    @Test
    void compradorNoVeLosFavoritosDeOtroComprador() throws Exception {
        favoriteRepo.saveAndFlush(new Favorite(compradorA, libroCentral));

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/favorites").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(libroCentral.getId()));
    }

    @Test
    void parametrosQueApuntanAOtraCuentaSeIgnoranAlListarFavoritos() throws Exception {
        favoriteRepo.saveAndFlush(new Favorite(compradorB, libroNorte));

        mvc.perform(get("/api/favorites")
                        .param("userId", String.valueOf(compradorB.getId()))
                        .param("user", String.valueOf(compradorB.getId()))
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void compradorNoPuedeQuitarElFavoritoDeOtroYRecibeElMismoErrorQueSiNoExistiera() throws Exception {
        favoriteRepo.saveAndFlush(new Favorite(compradorA, libroCentral));

        String ajeno = mvc.perform(delete("/api/favorites/{id}", libroCentral.getId())
                        .header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();
        String inexistente = mvc.perform(delete("/api/favorites/{id}", 999_999L)
                        .header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getErrorMessage();

        // No se revela que el recurso existe pero es de otra cuenta
        assertThat(ajeno).isEqualTo(inexistente);
        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorA.getId(), libroCentral.getId())).isTrue();
    }

    @Test
    void dosCompradoresPuedenFavoritearElMismoLibroYQuitarUnoNoAfectaAlOtro() throws Exception {
        mvc.perform(post("/api/favorites/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/favorites/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/favorites/{id}", libroCentral.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isNoContent());

        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorA.getId(), libroCentral.getId())).isFalse();
        assertThat(favoriteRepo.existsByUserIdAndBookId(compradorB.getId(), libroCentral.getId())).isTrue();
    }

    @Test
    void unDuenoNoPuedeLeerLosFavoritosDeUnComprador() throws Exception {
        favoriteRepo.saveAndFlush(new Favorite(compradorA, libroCentral));

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Recursos PRIVADOS: la libreria pertenece a su dueno, no al comprador
    // ------------------------------------------------------------------

    @Test
    void compradorNoPuedeVerNiDarDeBajaUnaLibreria() throws Exception {
        mvc.perform(get("/api/users/libreria").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/libreria/{id}", central.getId()).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isForbidden());

        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void duenoNoPuedeDarDeBajaLaLibreriaDeOtroDueno() throws Exception {
        mvc.perform(delete("/api/users/libreria/{id}", norte.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNotFound());

        assertThat(libraryRepo.findById(norte.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void duenoSiPuedeDarDeBajaSuPropiaLibreria() throws Exception {
        mvc.perform(delete("/api/users/libreria/{id}", central.getId()).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());

        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled()).isFalse();
    }
}