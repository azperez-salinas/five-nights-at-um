package librosbuysan.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

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
}