package librosbuysan.integration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import librosbuysan.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * RS28: el acceso otorgado por un token se puede cortar ANTES de que expire,
 * sin depender del vencimiento. Se cubren los tres mecanismos de invalidacion:
 *
 *   1. logout               -> el jti queda en TokenRevocationService
 *   2. cuenta deshabilitada -> el filtro consulta User.enabled (RS22)
 *   3. cambio de contrasena -> User.tokenVersion se incrementa y los tokens
 *                              con la version anterior dejan de servir
 *
 * En todos los casos el token se emite ANTES del evento y se comprueba que
 * funcionaba (control positivo); despues, que el mismo token da 401 y que un
 * token nuevo sigue funcionando. Siempre con un "Bearer" real por el filtro JWT.
 */
class TokenRevocationIntegrationTest extends IntegrationTestBase {

    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String PERFIL = "/api/users/me";
    private static final String LIBRERIA_PROPIA = "/api/users/libreria";
    private static final String ADMIN_PASSWORD = "/api/admin/password";
    private static final String OWNERS = "/api/admin/owners";
    private static final String NUEVA_PASSWORD = "NuevaClave456!";

    // ---------- 1. logout ----------

    @Test
    void elTokenDejaDeFuncionarDespuesDelLogoutYUnoNuevoSigueFuncionando() throws Exception {
        String viejo = bearer(compradorA);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, viejo)).andExpect(status().isOk());

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, viejo)).andExpect(status().isNoContent());

        mvc.perform(get(PERFIL).header(AUTHORIZATION, viejo)).andExpect(status().isUnauthorized());
        String nuevo = "Bearer " + login("comprador_a", PASSWORD);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, nuevo)).andExpect(status().isOk());
    }

    // ---------- 2. cuenta deshabilitada ----------

    @Test
    void elTokenDeUnDuenoDejaDeFuncionarSiSuCuentaSeDeshabilitaDespuesDeEmitido() throws Exception {
        String token = bearer(duenoCentral);
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isOk());

        ReflectionTestUtils.setField(duenoCentral, "enabled", false);
        userRepo.saveAndFlush(duenoCentral);

        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        // Los tokens de otras cuentas no se ven afectados
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, bearer(duenoNorte))).andExpect(status().isOk());
    }

    // ---------- 3. cambio de contrasena ----------

    @Test
    void cambiarLaPasswordInvalidaTodosLosTokensEmitidosAntes() throws Exception {
        // Dos sesiones abiertas (por ejemplo, celular y notebook) antes del cambio
        String celular = bearer(compradorA);
        String notebook = "Bearer " + login("comprador_a", PASSWORD);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, notebook)).andExpect(status().isOk());

        cambiarPassword(PERFIL, celular, status().isOk());

        // El token usado para el cambio y el de la otra sesion quedan inutiles
        mvc.perform(get(PERFIL).header(AUTHORIZATION, celular)).andExpect(status().isUnauthorized());
        mvc.perform(get(PERFIL).header(AUTHORIZATION, notebook)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/favorites").header(AUTHORIZATION, notebook)).andExpect(status().isUnauthorized());
    }

    @Test
    void despuesDelCambioDePasswordUnLoginNuevoFuncionaYLaPasswordViejaNo() throws Exception {
        cambiarPassword(PERFIL, bearer(compradorA), status().isOk());

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("comprador_a", PASSWORD)))
                .andExpect(status().isUnauthorized());

        String nuevo = "Bearer " + login("comprador_a", NUEVA_PASSWORD);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, nuevo)).andExpect(status().isOk());
    }

    @Test
    void elCambioDePasswordDeUnDuenoCortaElAccesoASuLibreriaConElTokenViejo() throws Exception {
        String viejo = bearer(duenoCentral);
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, viejo)).andExpect(status().isOk());

        cambiarPassword(PERFIL, viejo, status().isOk());

        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, viejo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, viejo)).andExpect(status().isUnauthorized());
        String nuevo = "Bearer " + login("dueno_central", NUEVA_PASSWORD);
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, nuevo)).andExpect(status().isOk());
    }

    @Test
    void elCambioDePasswordDelAdminInvalidaSusTokensAnteriores() throws Exception {
        String viejo = bearer(admin);
        String otraSesion = bearer(admin);

        cambiarPassword(ADMIN_PASSWORD, viejo, status().isNoContent());

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, otraSesion))
                .andExpect(status().isUnauthorized());
        String nuevo = "Bearer " + login("admin_test", NUEVA_PASSWORD);
        // Llega al controller (cuerpo invalido -> 400): el token nuevo autentica
        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, nuevo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reemplazarLaPasswordTemporalInvalidaTambienLosOtrosTokensAcotados() throws Exception {
        // Antes de RS28 solo se revocaba el token usado para el cambio
        String usado = bearer(duenoPendiente);
        String otro = bearer(duenoPendiente);

        cambiarPassword(PERFIL, usado, status().isOk());

        mvc.perform(get(PERFIL).header(AUTHORIZATION, otro)).andExpect(status().isUnauthorized());
        String nuevo = "Bearer " + login("dueno_pendiente", NUEVA_PASSWORD);
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, nuevo)).andExpect(status().isOk());
    }

    @Test
    void unCambioDePasswordFallidoNoInvalidaLosTokens() throws Exception {
        String token = bearer(compradorA);

        mvc.perform(put(PERFIL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrecta\",\"newPassword\":\"" + NUEVA_PASSWORD + "\"}")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isBadRequest());

        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isOk());
    }

    @Test
    void elCambioDePasswordDeUnUsuarioNoAfectaLosTokensDeOtros() throws Exception {
        String tokenB = bearer(compradorB);

        cambiarPassword(PERFIL, bearer(compradorA), status().isOk());

        mvc.perform(get(PERFIL).header(AUTHORIZATION, tokenB)).andExpect(status().isOk());
    }

    private void cambiarPassword(String ruta, String token, ResultMatcher esperado) throws Exception {
        mvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NUEVA_PASSWORD + "\"}")
                        .header(AUTHORIZATION, token))
                .andExpect(esperado);
    }

    private String login(String username, String password) throws Exception {
        String respuesta = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }

    private static String credenciales(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
