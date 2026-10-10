package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * R37: matriz completa de autorizacion.
 *
 * Cada fila declara, para UNA operacion del backend, que debe pasar cuando la
 * llama un anonimo, un COMPRADOR y un DUENO. La politica esperada queda
 * escrita en un solo lugar y se ejecuta contra la cadena de seguridad real.
 *
 * Hay dos tipos de filas:
 *  - ENDPOINTS_DE_LA_APP: los endpoints que existen de verdad.
 *  - RUTAS_SIN_REGLA: rutas/verbos que NO deben existir para nadie; prueban el
 *    deny-by-default (anyRequest().denyAll()) de SecurityConfig.
 *
 * El test "todaRutaDeLaAplicacionEstaEnLaMatriz" es la pieza clave de la
 * ultima oracion de R37 ("detectar que una operacion protegida quede
 * accesible sin verificacion"): lee los controllers reales y falla si alguien
 * agrega un endpoint sin escribir aca su politica esperada.
 */
class AuthorizationMatrixIntegrationTest extends IntegrationTestBase {

    enum Quien { ANONIMO, COMPRADOR, DUENO }

    enum Resultado {
        /** Pasa la autorizacion (despues el controller puede dar 200, 201, 400, 404...). */
        PERMITIDO,
        NO_AUTENTICADO_401,
        PROHIBIDO_403
    }

    record Caso(String metodo, String patron, String ruta, String body,
                Resultado anonimo, Resultado comprador, Resultado dueno) {

        Resultado para(Quien quien) {
            return switch (quien) {
                case ANONIMO -> anonimo;
                case COMPRADOR -> comprador;
                case DUENO -> dueno;
            };
        }

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    private static final Resultado OK = Resultado.PERMITIDO;
    private static final Resultado NO_AUTH = Resultado.NO_AUTENTICADO_401;
    private static final Resultado PROHIBIDO = Resultado.PROHIBIDO_403;

    // {id} / {bookId} se reemplazan en runtime por el id real de un libro de prueba.
    static final List<Caso> ENDPOINTS_DE_LA_APP = List.of(
            // Catalogo: publico para todos (R7, RF9, RS34 se filtra en la query, no aca)
            new Caso("GET", "/api/books", "/api/books", null, OK, OK, OK),
            new Caso("GET", "/api/books/search", "/api/books/search?q=quijote", null, OK, OK, OK),
            new Caso("GET", "/api/books/{id}", "/api/books/{id}", null, OK, OK, OK),

            // Favoritos: exclusivo del COMPRADOR (RF12, RS1, RS19)
            new Caso("GET", "/api/favorites", "/api/favorites", null, NO_AUTH, OK, PROHIBIDO),
            new Caso("POST", "/api/favorites/{bookId}", "/api/favorites/{bookId}", null, NO_AUTH, OK, PROHIBIDO),
            new Caso("DELETE", "/api/favorites/{bookId}", "/api/favorites/{bookId}", null, NO_AUTH, OK, PROHIBIDO),

            // Perfil propio: la unica funcionalidad compartida por ambos roles (RS5)
            new Caso("GET", "/api/users/me", "/api/users/me", null, NO_AUTH, OK, OK),
            new Caso("PUT", "/api/users/me", "/api/users/me", "{}", NO_AUTH, OK, OK),

            // Autenticacion: publica por definicion (no se puede exigir token para obtener un token)
            new Caso("POST", "/api/auth/register", "/api/auth/register", "{}", OK, OK, OK),
            new Caso("POST", "/api/auth/register-dueno", "/api/auth/register-dueno", "{}", OK, OK, OK),
            new Caso("POST", "/api/auth/login", "/api/auth/login", "{}", OK, OK, OK),
            // R3: cerrar sesion exige estar autenticado (cualquier rol). Debe estar
            // declarado ANTES de permitAll("/api/auth/**") en SecurityConfig.
            new Caso("POST", "/api/auth/logout", "/api/auth/logout", null, NO_AUTH, OK, OK));

    // Operaciones que hoy NO existen y por lo tanto nadie debe poder ejecutar.
    // Si manana alguien crea un POST /api/books sin tocar SecurityConfig, el
    // deny-by-default lo bloquea; si ademas agrega una regla permisiva, este
    // test falla y obliga a decidir el rol de forma explicita.
    static final List<Caso> RUTAS_SIN_REGLA = List.of(
            // Escritura sobre el catalogo (solo GET es publico)
            new Caso("POST", "/api/books", "/api/books", "{}", NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("PUT", "/api/books/{id}", "/api/books/{id}", "{}", NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("DELETE", "/api/books/{id}", "/api/books/{id}", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            // Un dueno tampoco puede escribir catalogo todavia (no esta implementado)
            new Caso("PATCH", "/api/books/{id}", "/api/books/{id}", "{}", NO_AUTH, PROHIBIDO, PROHIBIDO),
            // Acceso a cuentas AJENAS: solo existe /me
            new Caso("GET", "/api/users/{id}", "/api/users/1", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("GET", "/api/users", "/api/users", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("DELETE", "/api/users/me", "/api/users/me", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("POST", "/api/users/me", "/api/users/me", "{}", NO_AUTH, PROHIBIDO, PROHIBIDO),
            // Funcionalidades que podrian crearse (librerias, admin) y nacen cerradas
            new Caso("GET", "/api/libraries", "/api/libraries", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("GET", "/api/admin", "/api/admin", null, NO_AUTH, PROHIBIDO, PROHIBIDO),
            new Caso("GET", "/actuator/health", "/actuator/health", null, NO_AUTH, PROHIBIDO, PROHIBIDO));

    static Stream<Arguments> todosLosCasos() {
        return Stream.concat(ENDPOINTS_DE_LA_APP.stream(), RUTAS_SIN_REGLA.stream())
                .flatMap(caso -> Stream.of(Quien.values()).map(quien -> Arguments.of(caso, quien)));
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("todosLosCasos")
    void aplicaLaPoliticaDeAutorizacionEsperada(Caso caso, Quien quien) throws Exception {
        String idLibro = String.valueOf(libroCentral.getId());
        String ruta = caso.ruta().replace("{id}", idLibro).replace("{bookId}", idLibro);

        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(caso.metodo()), ruta);
        if (caso.body() != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(caso.body());
        }
        switch (quien) {
            case COMPRADOR -> req.header(AUTHORIZATION, bearer(compradorA));
            case DUENO -> req.header(AUTHORIZATION, bearer(duenoCentral));
            case ANONIMO -> {
                // sin header Authorization
            }
        }

        int status = mvc.perform(req).andReturn().getResponse().getStatus();
        String contexto = caso + " como " + quien + " devolvio " + status;

        switch (caso.para(quien)) {
            case PERMITIDO -> assertThat(status).as(contexto + " (esperaba que pasara la autorizacion)")
                    .isNotIn(401, 403);
            case NO_AUTENTICADO_401 -> assertThat(status).as(contexto + " (esperaba 401)").isEqualTo(401);
            case PROHIBIDO_403 -> assertThat(status).as(contexto + " (esperaba 403)").isEqualTo(403);
        }
    }

    // ------------------------------------------------------------------
    // Guardian de completitud
    // ------------------------------------------------------------------

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void todaRutaDeLaAplicacionEstaEnLaMatriz() {
        Set<String> reales = new TreeSet<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            // Solo controllers propios: se ignora, por ejemplo, el /error de Spring Boot
            if (!entry.getValue().getBeanType().getName().startsWith("librosbuysan")) {
                continue;
            }
            assertThat(info.getPathPatternsCondition()).isNotNull();
            Set<String> patrones = info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String patron : patrones) {
                if (metodos.isEmpty()) {
                    reales.add("CUALQUIER_METODO " + patron);
                }
                for (RequestMethod metodo : metodos) {
                    reales.add(metodo.name() + " " + patron);
                }
            }
        }

        Set<String> declarados = new TreeSet<>();
        ENDPOINTS_DE_LA_APP.forEach(c -> declarados.add(c.metodo() + " " + c.patron()));

        assertThat(reales)
                .as("Los endpoints reales de los controllers y los de ENDPOINTS_DE_LA_APP deben coincidir. "
                        + "Si agregaste un endpoint nuevo, declara aca (y en SecurityConfig) que roles pueden usarlo; "
                        + "si lo quitaste, borra su fila.")
                .containsExactlyInAnyOrderElementsOf(declarados);
    }

    // ------------------------------------------------------------------
    // Verbos no soportados sobre rutas protegidas
    // ------------------------------------------------------------------

    @Test
    void verboNoSoportadoSobreFavoritosNoSePermiteAUnDueno() throws Exception {
        // Un DUENO jamas debe llegar al controller de favoritos, ni siquiera con un verbo sin handler
        int status = mvc.perform(request(HttpMethod.PUT, "/api/favorites/" + libroCentral.getId())
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void verboNoSoportadoSobreFavoritosDeUnCompradorRecibe405() throws Exception {
        // La autorizacion pasa (es COMPRADOR) y Spring MVC responde que no hay handler para ese verbo (RS33)
        int status = mvc.perform(request(HttpMethod.PUT, "/api/favorites/" + libroCentral.getId())
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(405);
    }
}