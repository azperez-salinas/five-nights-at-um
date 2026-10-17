package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * R23: la decision de autorizacion combina RBAC y ABAC.
 *
 *   RBAC  -> el ROL decide a que funcionalidades generales se puede llegar
 *            (SecurityConfig): sin el rol correcto la respuesta es 403, y sin
 *            sesion 401.
 *   ABAC  -> con el rol correcto, los ATRIBUTOS del consumidor y del recurso
 *            deciden el caso concreto (BookService, LibraryService,
 *            FavoriteService y sus repositorios):
 *              consumidor: id de la cuenta del token, si tiene la contrasena
 *                          temporal (R18), si es dueno de una libreria habilitada
 *              recurso:    a que libreria pertenece un libro, si el libro y su
 *                          libreria estan habilitados (RS34)
 *
 * Las demas clases prueban cada capa por separado (AuthorizationMatrixIntegrationTest
 * el rol, BookOwnershipIntegrationTest / ResourceOwnershipIntegrationTest los
 * atributos). Esta clase prueba las dos JUNTAS: cada tabla fija UNA operacion
 * sobre UN recurso y varia solo el consumidor, de modo que se ve que el rol
 * DUENO por si solo no alcanza (mismo rol, distinto resultado segun el atributo).
 *
 * Convencion de los estados esperados:
 *   401 sin sesion | 403 falta el rol (RBAC) | 404 tiene el rol pero el recurso no
 *   le corresponde o no esta disponible (ABAC; el mismo 404 que si no existiera,
 *   para no revelar que el recurso existe, RS31/RS38).
 *
 * Escenario (ver IntegrationTestBase): duenoCentral es dueno de "central" (con
 * libroCentral); duenoNorte de "norte"; duenoBaja de "baja" (DESHABILITADA);
 * duenoPendiente todavia tiene la contrasena temporal.
 */
class RbacAbacIntegrationTest extends IntegrationTestBase {

    private static final String TITULO_ORIGINAL = "Don Quijote de la Mancha";
    private static final String CUERPO_EDICION = "{\"titulo\":\"Editado\"}";

    /** Quien llama. Los tres DUENO_* tienen el MISMO rol y distintos atributos. */
    enum Actor {
        ANONIMO,
        COMPRADOR,
        ADMIN,
        DUENO_CON_PASSWORD_TEMPORAL,
        DUENO_PROPIETARIO,
        DUENO_DE_OTRA_LIBRERIA,
        DUENO_CON_LIBRERIA_DESHABILITADA
    }

    private String token(Actor actor) {
        return switch (actor) {
            case ANONIMO -> null;
            case COMPRADOR -> bearer(compradorA);
            case ADMIN -> bearer(admin);
            case DUENO_CON_PASSWORD_TEMPORAL -> bearer(duenoPendiente);
            case DUENO_PROPIETARIO -> bearer(duenoCentral);
            case DUENO_DE_OTRA_LIBRERIA -> bearer(duenoNorte);
            case DUENO_CON_LIBRERIA_DESHABILITADA -> bearer(duenoBaja);
        };
    }

    private MockHttpServletRequestBuilder como(Actor actor, MockHttpServletRequestBuilder request) {
        String token = token(actor);
        return token == null ? request : request.header(AUTHORIZATION, token);
    }

    // ------------------------------------------------------------------
    // Operacion sobre un recurso de "central" (libro o libreria): cada fila es un
    // consumidor distinto. Solo DUENO_PROPIETARIO pasa RBAC y ABAC.
    // ------------------------------------------------------------------

    private static Stream<Arguments> sobreUnRecursoDeCentral(int estadoDelPropietario) {
        return Stream.of(
                Arguments.of(Actor.ANONIMO, 401),
                Arguments.of(Actor.COMPRADOR, 403),
                Arguments.of(Actor.ADMIN, 403),
                // Rol DUENO, pero con la contrasena temporal el token solo sirve para cambiarla
                Arguments.of(Actor.DUENO_CON_PASSWORD_TEMPORAL, 403),
                Arguments.of(Actor.DUENO_PROPIETARIO, estadoDelPropietario),
                // Rol DUENO correcto, pero el recurso es de otra libreria
                Arguments.of(Actor.DUENO_DE_OTRA_LIBRERIA, 404),
                // Rol DUENO correcto, pero SU libreria esta deshabilitada: pierde sus permisos
                Arguments.of(Actor.DUENO_CON_LIBRERIA_DESHABILITADA, 404));
    }

    static Stream<Arguments> editarLibroDeCentral() {
        return sobreUnRecursoDeCentral(200);
    }

    static Stream<Arguments> borrarLibroDeCentral() {
        return sobreUnRecursoDeCentral(204);
    }

    static Stream<Arguments> darDeBajaCentral() {
        return sobreUnRecursoDeCentral(204);
    }

    @ParameterizedTest(name = "PUT /api/books/libro de central como {0} -> {1}")
    @MethodSource("editarLibroDeCentral")
    void editarUnLibroExigeElRolDuenoYQueElLibroSeaDeSuLibreria(Actor actor, int esperado) throws Exception {
        mvc.perform(como(actor, put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDICION)))
                .andExpect(status().is(esperado));

        String titulo = bookRepo.findById(libroCentral.getId()).orElseThrow().getTitulo();
        if (esperado == 200) {
            assertThat(titulo).isEqualTo("Editado");
        } else {
            assertThat(titulo).as("una operacion rechazada no debe modificar el libro").isEqualTo(TITULO_ORIGINAL);
        }
    }

    @ParameterizedTest(name = "DELETE /api/books/libro de central como {0} -> {1}")
    @MethodSource("borrarLibroDeCentral")
    void borrarUnLibroExigeElRolDuenoYQueElLibroSeaDeSuLibreria(Actor actor, int esperado) throws Exception {
        mvc.perform(como(actor, delete("/api/books/{id}", libroCentral.getId())))
                .andExpect(status().is(esperado));

        boolean sigueHabilitado = bookRepo.findById(libroCentral.getId()).orElseThrow().isEnabled();
        assertThat(sigueHabilitado)
                .as("el libro solo debe darse de baja si la operacion fue autorizada")
                .isEqualTo(esperado != 204);
    }

    @ParameterizedTest(name = "DELETE /api/users/libreria/central como {0} -> {1}")
    @MethodSource("darDeBajaCentral")
    void darDeBajaUnaLibreriaExigeElRolDuenoYSerSuDueno(Actor actor, int esperado) throws Exception {
        mvc.perform(como(actor, delete("/api/users/libreria/{id}", central.getId())))
                .andExpect(status().is(esperado));

        boolean sigueHabilitada = libraryRepo.findById(central.getId()).orElseThrow().isEnabled();
        assertThat(sigueHabilitada)
                .as("la libreria solo debe darse de baja si la operacion fue autorizada")
                .isEqualTo(esperado != 204);
    }

    // ------------------------------------------------------------------
    // Metricas: el recurso es "la libreria del consumidor", asi que aca el atributo
    // decisivo es del CONSUMIDOR (tiene una libreria propia habilitada).
    // ------------------------------------------------------------------

    static Stream<Arguments> metricas() {
        return Stream.of(
                Arguments.of(Actor.ANONIMO, 401),
                Arguments.of(Actor.COMPRADOR, 403),
                Arguments.of(Actor.ADMIN, 403),
                Arguments.of(Actor.DUENO_CON_PASSWORD_TEMPORAL, 403),
                // Dos duenos distintos reciben 200, cada uno con los datos de SU libreria
                // (el contenido esta acotado y se prueba en LibraryMetricsIntegrationTest)
                Arguments.of(Actor.DUENO_PROPIETARIO, 200),
                Arguments.of(Actor.DUENO_DE_OTRA_LIBRERIA, 200),
                // Mismo rol, pero sin libreria habilitada no hay nada que consultar
                Arguments.of(Actor.DUENO_CON_LIBRERIA_DESHABILITADA, 404));
    }

    @ParameterizedTest(name = "GET /api/users/libreria/metricas como {0} -> {1}")
    @MethodSource("metricas")
    void lasMetricasExigenElRolDuenoYUnaLibreriaPropiaHabilitada(Actor actor, int esperado) throws Exception {
        mvc.perform(como(actor, get("/api/users/libreria/metricas"))).andExpect(status().is(esperado));
    }

    // ------------------------------------------------------------------
    // Favoritos: rol COMPRADOR x atributos del RECURSO (libro y libreria habilitados)
    // ------------------------------------------------------------------

    static Stream<Arguments> favoritearUnLibroHabilitado() {
        return Stream.of(
                Arguments.of(Actor.ANONIMO, 401),
                Arguments.of(Actor.COMPRADOR, 201),
                Arguments.of(Actor.ADMIN, 403),
                Arguments.of(Actor.DUENO_PROPIETARIO, 403),
                Arguments.of(Actor.DUENO_CON_PASSWORD_TEMPORAL, 403));
    }

    @ParameterizedTest(name = "POST /api/favorites/libro habilitado como {0} -> {1}")
    @MethodSource("favoritearUnLibroHabilitado")
    void favoritearExigeElRolComprador(Actor actor, int esperado) throws Exception {
        mvc.perform(como(actor, post("/api/favorites/{id}", libroCentral.getId())))
                .andExpect(status().is(esperado));

        assertThat(favoriteRepo.count()).isEqualTo(esperado == 201 ? 1 : 0);
    }

    enum EstadoDelLibro {
        HABILITADO(201),
        DE_LIBRERIA_DESHABILITADA(404),
        DADO_DE_BAJA_POR_SU_DUENO(404),
        INEXISTENTE(404);

        final int esperado;

        EstadoDelLibro(int esperado) {
            this.esperado = esperado;
        }
    }

    @ParameterizedTest(name = "el comprador favoritea un libro {0}")
    @EnumSource(EstadoDelLibro.class)
    void conElRolCompradorElAtributoDelLibroDecideSiSePuedeFavoritear(EstadoDelLibro estado) throws Exception {
        long idLibro = switch (estado) {
            case HABILITADO -> libroCentral.getId();
            case DE_LIBRERIA_DESHABILITADA -> libroBaja.getId();
            case DADO_DE_BAJA_POR_SU_DUENO -> {
                libroCentral.disable();
                bookRepo.saveAndFlush(libroCentral);
                yield libroCentral.getId();
            }
            case INEXISTENTE -> 999_999L;
        };

        mvc.perform(post("/api/favorites/{id}", idLibro).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().is(estado.esperado));

        assertThat(favoriteRepo.count()).isEqualTo(estado.esperado == 201 ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // La decision se evalua en CADA request con el estado ACTUAL: la misma cuenta, el
    // mismo token y la misma operacion cambian de resultado cuando cambia un atributo.
    // ------------------------------------------------------------------

    @Test
    void elMismoCompradorPierdeElAccesoAlLibroCuandoSuLibreriaSeDeshabilita() throws Exception {
        String token = bearer(compradorA);
        String favorito = "/api/favorites/{id}";

        mvc.perform(post(favorito, libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isCreated());
        // idempotente mientras el libro este disponible
        mvc.perform(post(favorito, libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isOk());

        central.disable();
        libraryRepo.saveAndFlush(central);

        // Ya era favorito, pero el atributo del recurso cambio: se rechaza igual que si no existiera.
        // (Antes de R23 este caso respondia 200 porque la idempotencia se evaluaba primero.)
        mvc.perform(post(favorito, libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        // El libro deja de listarse...
        mvc.perform(get("/api/favorites").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // ...pero el usuario siempre puede limpiar SU propio favorito
        mvc.perform(delete(favorito, libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(favoriteRepo.count()).isZero();
    }

    @Test
    void elMismoDuenoPierdeElAccesoASuLibroCuandoSuLibreriaSeDaDeBaja() throws Exception {
        String token = bearer(duenoCentral);

        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDICION)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/users/libreria/{id}", central.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        // Mismo token, mismo rol, mismo libro: el atributo "libreria habilitada" cambio
        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDICION)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void lasDecisionesDeUnConsumidorNoDependenDeDatosQueEnvieEnLaRequest() throws Exception {
        // Los atributos del consumidor salen SIEMPRE del token/base, nunca del cliente:
        // un dueno ajeno no gana acceso aunque mande el id/dueno/libreria de otro
        String cuerpo = "{\"titulo\":\"Editado\",\"libreriaId\":" + central.getId()
                + ",\"duenoId\":" + duenoCentral.getId() + ",\"userId\":" + duenoCentral.getId() + "}";

        mvc.perform(put("/api/books/{id}", libroCentral.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(AUTHORIZATION, bearer(duenoNorte))
                        .param("libreriaId", String.valueOf(central.getId()))
                        .param("userId", String.valueOf(duenoCentral.getId())))
                .andExpect(status().isNotFound());

        assertThat(bookRepo.findById(libroCentral.getId()).orElseThrow().getTitulo()).isEqualTo(TITULO_ORIGINAL);
    }
}