package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import librosbuysan.support.IntegrationTestBase;
import librosbuysan.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * R10: un cambio en el permiso (rol) vigente de un consumidor debe aplicarse
 * a las decisiones de autorizacion antes de permitir nuevas operaciones.
 *
 * El caso que justifica estos tests es el mismo que en RS22: el JWT es
 * stateless y lleva el rol fijado al momento de emitirse. Si el rol cambia
 * despues (revocacion, degradacion o ascenso), la firma y la expiracion del
 * token siguen siendo validas, asi que sin una consulta al rol ACTUAL de la
 * cuenta el token viejo seguiria autorizando operaciones del rol que ya no
 * tiene. Por eso en cada test el token se genera con el rol original y recien
 * despues se actualiza el rol directamente en la base (no existe, ni deberia
 * existir, un endpoint para cambiar el rol de otro usuario).
 *
 * Siempre se manda un "Bearer" real (no @WithMockUser): ese atajo se salta el
 * filtro JWT, que es justamente lo que aca se quiere probar.
 */
class RoleChangeIntegrationTest extends IntegrationTestBase {

    private static final String LOGIN = "/api/auth/login";
    private static final String PERFIL = "/api/users/me";
    private static final String FAVORITOS = "/api/favorites";
    private static final String LIBRERIA_PROPIA = "/api/users/libreria";
    private static final String OWNERS = "/api/admin/owners";
    private static final String ADMIN_PASSWORD = "/api/admin/password";

    /** Mismo atajo que usa DisabledAccountIntegrationTest: la entidad no expone un setter de rol. */
    private void cambiarRol(User user, User.Role nuevoRol) {
        ReflectionTestUtils.setField(user, "role", nuevoRol);
        userRepo.saveAndFlush(user);
    }

    // ---------- revocacion: degradar pierde el acceso del rol anterior ----------

    @Test
    void elTokenYaEmitidoDeUnCompradorDejaDeAutorizarFavoritosAlDegradarloADueno() throws Exception {
        String token = bearer(compradorA);

        // Control positivo: con el rol original el mismo token funciona
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isOk());

        cambiarRol(compradorA, User.Role.DUENO);

        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elDuenoDegradadoACompradorPierdeElAccesoASuLibreriaConElTokenViejo() throws Exception {
        String token = bearer(duenoCentral);
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isOk()); // control

        cambiarRol(duenoCentral, User.Role.COMPRADOR);

        // El token sigue diciendo DUENO: no solo pierde /libreria, tampoco gana
        // /favorites (COMPRADOR) solo porque el rol actual ya sea ese
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elAdminDegradadoPierdeLasAccionesDelPanelConSuTokenViejo() throws Exception {
        String token = bearer(admin);
        String cuerpo = "{\"username\":\"nuevo_dueno\",\"email\":\"nuevo@test.local\",\"nombreLibreria\":\"Nueva\"}";

        cambiarRol(admin, User.Role.COMPRADOR);

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        assertThat(userRepo.findByUsername("nuevo_dueno")).as("no se debe haber creado el dueno").isEmpty();
    }

    // ---------- un ascenso tampoco habilita el rol nuevo con el token viejo ----------

    @Test
    void unCompradorAscendidoADuenoNoPuedeUsarOperacionesDeDuenoConSuTokenViejo() throws Exception {
        String token = bearer(compradorA);

        cambiarRol(compradorA, User.Role.DUENO);

        // El claim sigue diciendo COMPRADOR: ni conserva favoritos ni gana libreria
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    // ---------- alcance y efectos colaterales ----------

    @Test
    void cambiarElRolDeUnUsuarioNoAfectaALosDemas() throws Exception {
        String tokenA = bearer(compradorA);
        String tokenB = bearer(compradorB);
        String tokenDueno = bearer(duenoCentral);

        cambiarRol(compradorA, User.Role.DUENO);

        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, tokenA)).andExpect(status().isUnauthorized());
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, tokenB)).andExpect(status().isOk());
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, tokenDueno)).andExpect(status().isOk());
    }

    @Test
    void elRechazoEsInmediatoYNoEsperaALaExpiracionDelToken() throws Exception {
        // El token de un comprador vive hasta 60 min: el rechazo no puede depender de que venza
        String token = bearer(compradorA);
        cambiarRol(compradorA, User.Role.DUENO);

        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenConRolDesactualizadoEnUnEndpointPublicoSeTrataComoAnonimo() throws Exception {
        // Igual que un token revocado o de cuenta deshabilitada: no rompe el
        // catalogo publico, solo deja de identificar a la cuenta
        String token = bearer(compradorA);
        cambiarRol(compradorA, User.Role.DUENO);

        mvc.perform(get("/api/books").header(AUTHORIZATION, token)).andExpect(status().isOk());
    }

    // ---------- recorrido real con login: el token nuevo si refleja el rol vigente ----------

    @Test
    void recorridoRealLoginCambioDeRolYNuevoLoginConElRolActualizado() throws Exception {
        // Token emitido por /login (no por JwtService directo): es el flujo del usuario real
        String token = "Bearer " + loginYObtenerToken("comprador_a");
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isOk());

        cambiarRol(compradorA, User.Role.ADMIN);

        // 1) el token que ya tenia (COMPRADOR) deja de servir, incluso para lo que ya podia hacer...
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());

        // 2) ...pero un login nuevo si emite un token con el rol vigente (ADMIN)
        String tokenNuevo = "Bearer " + loginYObtenerToken("comprador_a");
        String cuerpo = "{\"username\":\"nuevo_dueno\",\"email\":\"nuevo@test.local\",\"nombreLibreria\":\"Nueva\"}";
        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(AUTHORIZATION, tokenNuevo))
                .andExpect(status().isCreated());

        // 3) y ya no conserva los permisos del rol anterior
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, tokenNuevo)).andExpect(status().isForbidden());
    }

    @Test
    void elPerfilPropioTambienRespetaElRolVigenteParaRolesCompartidos() throws Exception {
        // /users/me lo pueden ver tanto COMPRADOR como DUENO: no alcanza con
        // estar autenticado, el rol usado debe ser el vigente en cada momento
        String token = bearer(duenoCentral);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isOk()); // control

        cambiarRol(duenoCentral, User.Role.COMPRADOR);

        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    private String loginYObtenerToken(String username) throws Exception {
        String respuesta = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }

    private static String credenciales(String username) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}";
    }
}
