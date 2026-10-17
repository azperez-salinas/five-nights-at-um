package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.net.URI;
import java.util.stream.Stream;
import librosbuysan.support.IntegrationTestBase;
import librosbuysan.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RX40: ninguna operacion protegida puede ejecutarse por una ruta o solicitud
 * que esquive el control de acceso.
 *
 * AuthorizationMatrixIntegrationTest (R37) ya prueba, por rol, la politica de
 * cada endpoint con su ruta canonica y que no haya endpoints fuera de la
 * matriz. Lo que no prueba es que una VARIANTE de la ruta (mayusculas, barra
 * final, "..", ";", %2e, barras dobles) o un truco de verbo (_method,
 * X-HTTP-Method-Override) no termine en el handler protegido. Spring
 * Security y Spring MVC no interpretan la URL igual: si una regla permitAll
 * matcheara una variante que MVC despues enruta a un controller protegido, la
 * operacion se ejecutaria sin control.
 *
 * Por eso aca no alcanza con mirar el status: despues de cada intento se
 * verifica en la base que la operacion NO se ejecuto (el libro y la libreria
 * siguen habilitados, no se creo ningun dueno ni favorito y la contrasena no
 * cambio).
 */
class AccessControlBypassIntegrationTest extends IntegrationTestBase {

    enum Quien { ANONIMO, COMPRADOR, DUENO, ADMIN }

    private long usuariosAntes;
    // Todos los usuarios del escenario comparten el mismo hash (IntegrationTestBase)
    private String passwordHashAntes;

    @BeforeEach
    void guardarEstadoInicial() {
        usuariosAntes = userRepo.count();
        passwordHashAntes = compradorA.getPasswordHash();
    }

    // {libro} y {libreria} se reemplazan por los ids reales de libroCentral y
    // central. Cada actor NO tiene permiso para la operacion de la ruta.
    static Stream<Arguments> variantesDeRutasProtegidas() {
        return Stream.of(
                // DELETE /api/books/{id}: exclusivo del DUENO
                Arguments.of("DELETE", "/api/books/{libro}/", Quien.COMPRADOR),
                Arguments.of("DELETE", "/API/books/{libro}", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/Books/{libro}", Quien.ANONIMO),
                Arguments.of("DELETE", "/api//books/{libro}", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/books/./{libro}", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/books/{libro};x=y", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/books/%2e/{libro}", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/auth/../books/{libro}", Quien.ANONIMO),
                // GET /api/books/mine: exclusivo del DUENO, pero GET /api/books/{id} es publico
                Arguments.of("GET", "/api/books/mine/", Quien.ANONIMO),
                Arguments.of("GET", "/api/books/MINE", Quien.ANONIMO),
                Arguments.of("GET", "/api/books/mine;x=y", Quien.COMPRADOR),
                Arguments.of("HEAD", "/api/books/mine", Quien.ANONIMO),
                // DELETE /api/users/libreria/{id}: exclusivo del DUENO
                Arguments.of("DELETE", "/api/users/libreria/{libreria}/", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/users/LIBRERIA/{libreria}", Quien.COMPRADOR),
                Arguments.of("DELETE", "/api/users/libreria/%2e%2e/libreria/{libreria}", Quien.ANONIMO),
                // POST /api/admin/owners: exclusivo del ADMIN; /api/auth/** es publico
                Arguments.of("POST", "/api/auth/../admin/owners", Quien.ANONIMO),
                Arguments.of("POST", "/api/auth/%2e%2e/admin/owners", Quien.ANONIMO),
                Arguments.of("POST", "/api/auth/..%2fadmin/owners", Quien.ANONIMO),
                Arguments.of("POST", "/api/admin/owners/", Quien.COMPRADOR),
                Arguments.of("POST", "/api/Admin/owners", Quien.COMPRADOR),
                Arguments.of("POST", "/api/admin//owners", Quien.ANONIMO),
                Arguments.of("POST", "/api/admin/owners;jsessionid=x", Quien.COMPRADOR),
                Arguments.of("POST", "/error/../api/admin/owners", Quien.ANONIMO),
                // PUT /api/admin/password: exclusivo del ADMIN
                Arguments.of("PUT", "/api/auth/../admin/password", Quien.COMPRADOR),
                // POST /api/favorites/{bookId}: exclusivo del COMPRADOR
                Arguments.of("POST", "/api/Favorites/{libro}", Quien.ADMIN),
                Arguments.of("POST", "/api/auth/../favorites/{libro}", Quien.ANONIMO),
                // PUT /api/users/me: el admin no puede usarlo
                Arguments.of("PUT", "/api/users/me/", Quien.ADMIN),
                Arguments.of("PUT", "/api/auth/../users/me", Quien.ANONIMO));
    }

    @ParameterizedTest(name = "{0} {1} como {2}")
    @MethodSource("variantesDeRutasProtegidas")
    void unaVarianteDeLaRutaNoEjecutaLaOperacionProtegida(String metodo, String ruta, Quien quien)
            throws Exception {
        String uri = ruta.replace("{libro}", String.valueOf(libroCentral.getId()))
                .replace("{libreria}", String.valueOf(central.getId()));

        int status = ejecutar(request(HttpMethod.valueOf(metodo), URI.create(uri)), quien);

        assertThat(status).as(metodo + " " + uri + " como " + quien + " devolvio " + status
                + " (esperaba un rechazo 4xx)").isBetween(400, 499);
        assertQueNoSeEjecutoNingunaOperacionProtegida();
    }

    // Spring Boot no registra HiddenHttpMethodFilter por defecto y nada lee
    // X-HTTP-Method-Override: un verbo permitido (o publico) no puede
    // "convertirse" en uno protegido.
    static Stream<Arguments> trucosDeVerbo() {
        return Stream.of(
                Arguments.of("GET", "/api/books/{libro}", "X-HTTP-Method-Override", "DELETE", Quien.ANONIMO),
                Arguments.of("GET", "/api/books/{libro}", "X-HTTP-Method", "DELETE", Quien.COMPRADOR),
                Arguments.of("POST", "/api/books/{libro}?_method=DELETE", null, null, Quien.COMPRADOR),
                Arguments.of("POST", "/api/auth/login?_method=DELETE", "X-HTTP-Method-Override", "DELETE",
                        Quien.ANONIMO),
                Arguments.of("GET", "/api/librerias", "X-HTTP-Method-Override", "DELETE", Quien.COMPRADOR));
    }

    @ParameterizedTest(name = "{0} {1} con {2}: {3} como {4}")
    @MethodSource("trucosDeVerbo")
    void sobrescribirElVerboNoEjecutaLaOperacionProtegida(String metodo, String ruta, String header,
                                                          String valor, Quien quien) throws Exception {
        String uri = ruta.replace("{libro}", String.valueOf(libroCentral.getId()));
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(metodo), URI.create(uri));
        if (header != null) {
            req.header(header, valor);
        }

        ejecutar(req, quien);

        // El status puede ser 200 (el GET publico se atiende como GET) o 4xx:
        // lo que importa es que el DELETE nunca se haya ejecutado.
        assertQueNoSeEjecutoNingunaOperacionProtegida();
    }

    // Control positivo: con la ruta canonica y el rol correcto, el MISMO
    // request si ejecuta la operacion. Sin esto, los asserts de arriba podrian
    // pasar solo porque el body o la verificacion de la base no sirven.
    @Test
    void conLaRutaCanonicaYElRolCorrectoLaOperacionSiSeEjecuta() throws Exception {
        assertThat(ejecutar(request(HttpMethod.POST, URI.create("/api/admin/owners")), Quien.ADMIN))
                .isEqualTo(201);
        assertThat(userRepo.count()).isEqualTo(usuariosAntes + 1);

        assertThat(ejecutar(request(HttpMethod.DELETE, URI.create("/api/books/" + libroCentral.getId())),
                Quien.DUENO)).isBetween(200, 299);
        assertThat(bookRepo.findById(libroCentral.getId())).get().matches(b -> !b.isEnabled());
    }

    private int ejecutar(MockHttpServletRequestBuilder req, Quien quien) throws Exception {
        User usuario = switch (quien) {
            case ANONIMO -> null;
            case COMPRADOR -> compradorA;
            case DUENO -> duenoCentral;
            case ADMIN -> admin;
        };
        if (usuario != null) {
            req.header(AUTHORIZATION, bearer(usuario));
        }
        // Body valido para POST /api/admin/owners y /api/favorites: si alguna
        // variante llegara al handler, la operacion se ejecutaria de verdad
        // y la verificacion de la base lo detectaria.
        req.contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"intruso","email":"intruso@test.local","nombreLibreria":"Intrusa",
                 "currentPassword":"%s","newPassword":"OtraPassword123!"}""".formatted(PASSWORD));
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private void assertQueNoSeEjecutoNingunaOperacionProtegida() {
        assertThat(bookRepo.findById(libroCentral.getId())).get()
                .as("el libro no debe haberse dado de baja").matches(b -> b.isEnabled());
        assertThat(libraryRepo.findById(central.getId())).get()
                .as("la libreria no debe haberse dado de baja").matches(l -> l.isEnabled());
        assertThat(userRepo.count()).as("no debe haberse creado ningun usuario").isEqualTo(usuariosAntes);
        assertThat(favoriteRepo.count()).as("no debe haberse creado ningun favorito").isZero();
        assertThat(userRepo.findAll())
                .as("ninguna contrasena debe haber cambiado")
                .allMatch(u -> u.getPasswordHash().equals(passwordHashAntes));
    }
}
