package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * R3: cerrar la sesion activa. Los unitarios prueban cada pieza aislada; aca
 * se comprueba el recorrido completo con la cadena de seguridad REAL, que es
 * donde se rompen cosas como el orden de las reglas en SecurityConfig.
 *
 * Siempre se manda un "Bearer" real (no @WithMockUser): ese atajo se salta el
 * filtro JWT, que es justamente lo que aca se quiere probar.
 */
class LogoutIntegrationTest extends IntegrationTestBase {

    private static final String LOGOUT = "/api/auth/logout";
    private static final String RUTA_PROTEGIDA = "/api/users/me";
    private static final String LOGIN = "/api/auth/login";
    private static final String OWNERS = "/api/admin/owners";
    private static final String LIBRERIA_PROPIA = "/api/users/libreria";

    @Test
    void logoutDeUnCompradorDevuelve204SinCuerpo() throws Exception {
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void logoutDeUnDuenoDevuelve204() throws Exception {
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isNoContent());
    }

    @Test
    void despuesDelLogoutElMismoTokenYaNoDaAcceso() throws Exception {
        String token = bearer(compradorA);

        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, token))
                .andExpect(status().isOk());                       // control positivo
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());             // el token ya no sirve
    }

    @Test
    void elTokenRevocadoTampocoSirveEnLasRutasPorRol() throws Exception {
        // Vale para cualquier ruta protegida, no solo /me: el filtro corre antes que todas
        String token = bearer(compradorA);

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSoloCierraLaSesionDelTokenUsadoNoLasDemasDelMismoUsuario() throws Exception {
        // Dos tokens validos del mismo usuario (dos dispositivos): cerrar uno no cierra el otro
        String celular = bearer(compradorA);
        String notebook = bearer(compradorA);

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, celular)).andExpect(status().isNoContent());

        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, celular))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, notebook))
                .andExpect(status().isOk());
    }

    @Test
    void logoutNoAfectaLaSesionDeOtroUsuario() throws Exception {
        String tokenA = bearer(compradorA);
        String tokenB = bearer(compradorB);

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, tokenA)).andExpect(status().isNoContent());

        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void repetirElLogoutConElMismoTokenDa401PorqueYaNoEstaAutenticado() throws Exception {
        String token = bearer(compradorA);

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSinTokenEs401() throws Exception {
        // Falla si la regla de logout queda DESPUES de permitAll("/api/auth/**"):
        // el endpoint seria publico y el principal llegaria null al controller
        mvc.perform(post(LOGOUT)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutConTokenInvalidoEs401() throws Exception {
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, "Bearer esto.no.es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenRevocadoEnUnEndpointPublicoSeTrataComoAnonimo() throws Exception {
        // Igual que un token invalido: no rompe el catalogo publico, solo deja de identificar al usuario
        String token = bearer(compradorA);
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(get("/api/books").header(AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    // ---------- Cobertura completa del requisito: todos los roles y tipos de operacion ----------

    @Test
    void despuesDelLogoutDeUnAdminSuTokenYaNoAdministra() throws Exception {
        String token = bearer(admin);
        String cuerpoVacio = "{}";

        // Control positivo: el token vigente llega al controller (cuerpo invalido -> 400, no 401/403)
        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(cuerpoVacio)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isBadRequest());

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(cuerpoVacio)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unDuenoConPasswordTemporalTambienPuedeCerrarSesionYSuTokenQuedaInutil() throws Exception {
        // Su token lleva el rol acotado PWD_CHANGE: no debe quedar atrapado sin poder salir
        String token = bearer(duenoPendiente);

        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenRevocadoDeUnDuenoNoAccedeASuLibreria() throws Exception {
        String token = bearer(duenoCentral);

        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenRevocadoNoPuedeEjecutarOperacionesDeEscrituraNiDestructivas() throws Exception {
        // Distinto de un GET: aca ademas se comprueba que el estado NO cambia
        String token = bearer(duenoCentral);
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isNoContent());

        mvc.perform(put(RUTA_PROTEGIDA).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(LIBRERIA_PROPIA + "/{id}", central.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());

        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled())
                .as("la libreria no debe haberse dado de baja con un token revocado").isTrue();
    }

    @Test
    void recorridoRealLoginLogoutYNuevoLoginElTokenViejoMuereYElNuevoFunciona() throws Exception {
        // Con tokens emitidos por /login (no por JwtService directo): es el flujo del usuario real
        String tokenViejo = "Bearer " + loginYObtenerToken("comprador_a");

        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, tokenViejo)).andExpect(status().isOk());
        mvc.perform(post(LOGOUT).header(AUTHORIZATION, tokenViejo)).andExpect(status().isNoContent());
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, tokenViejo)).andExpect(status().isUnauthorized());

        // Cerrar sesion no bloquea la cuenta: puede volver a entrar...
        String tokenNuevo = "Bearer " + loginYObtenerToken("comprador_a");
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, tokenNuevo)).andExpect(status().isOk());
        // ...y el login nuevo no "resucita" el token anterior
        mvc.perform(get(RUTA_PROTEGIDA).header(AUTHORIZATION, tokenViejo)).andExpect(status().isUnauthorized());
    }

    private String loginYObtenerToken(String username) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
        String respuesta = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}