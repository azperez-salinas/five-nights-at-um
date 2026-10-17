package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * R22: una cuenta deshabilitada no puede usarse para acceder a recursos protegidos.
 *
 * El caso que justifica estos tests es el token EMITIDO ANTES de la
 * deshabilitacion: un JWT es stateless, su firma y su expiracion siguen siendo
 * validas, asi que sin una consulta al estado actual de la cuenta seguiria
 * funcionando hasta 60 minutos. Por eso en cada test el token se genera
 * mientras la cuenta esta habilitada y recien despues se la deshabilita.
 *
 * Siempre se manda un "Bearer" real (no @WithMockUser): ese atajo se salta el
 * filtro JWT, que es justamente lo que aca se quiere probar.
 */
class DisabledAccountIntegrationTest extends IntegrationTestBase {

    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String PERFIL = "/api/users/me";
    private static final String FAVORITOS = "/api/favorites";
    private static final String LIBRERIA_PROPIA = "/api/users/libreria";
    private static final String OWNERS = "/api/admin/owners";
    private static final String ADMIN_PASSWORD = "/api/admin/password";

    /** Mismo atajo que usa AuthServiceTest: la entidad no expone un setter de enabled. */
    private void deshabilitar(User user) {
        ReflectionTestUtils.setField(user, "enabled", false);
        userRepo.saveAndFlush(user);
    }

    // ---------- el caso central: token emitido antes de deshabilitar ----------

    @Test
    void elTokenYaEmitidoDeUnCompradorDejaDeFuncionarAlDeshabilitarLaCuenta() throws Exception {
        String token = bearer(compradorA);

        // Control positivo: con la cuenta habilitada el mismo token funciona
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isOk());

        deshabilitar(compradorA);

        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elRechazoVaLoMasAllaDelPerfilEnCualquierRutaProtegida() throws Exception {
        // Antes de R22 solo GET/PUT /me (que usan loadActiveUser) detectaban la cuenta
        // deshabilitada; el resto de las rutas confiaba ciegamente en el token
        String tokenComprador = bearer(compradorA);
        String tokenDueno = bearer(duenoCentral);
        deshabilitar(compradorA);
        deshabilitar(duenoCentral);

        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, tokenComprador)).andExpect(status().isUnauthorized());
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, tokenDueno)).andExpect(status().isUnauthorized());
        mvc.perform(get(LIBRERIA_PROPIA + "/metricas").header(AUTHORIZATION, tokenDueno))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books/mine").header(AUTHORIZATION, tokenDueno)).andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDeUnCompradorDeshabilitadoNoLeeNiModificaFavoritos() throws Exception {
        // Ex GAP-1 de KnownAuthorizationGapsIntegrationTest, resuelto por R22
        String token = bearer(compradorA);
        deshabilitar(compradorA);

        mvc.perform(get(FAVORITOS).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(post(FAVORITOS + "/{id}", libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        assertThat(favoriteRepo.count()).as("no se debe haber agregado el favorito").isZero();
    }

    // ---------- cada rol ----------

    @Test
    void elDuenoDeshabilitadoNoPuedeEjecutarOperacionesDestructivasConSuTokenViejo() throws Exception {
        String token = bearer(duenoCentral);
        deshabilitar(duenoCentral);

        mvc.perform(delete(LIBRERIA_PROPIA + "/{id}", central.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());

        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled())
                .as("la libreria no debe haberse dado de baja con una cuenta deshabilitada").isTrue();
    }

    @Test
    void elAdminDeshabilitadoPierdeLasAccionesDelPanel() throws Exception {
        String token = bearer(admin);
        String cuerpo = "{\"username\":\"nuevo_dueno\",\"email\":\"nuevo@test.local\",\"nombreLibreria\":\"Nueva\"}";

        deshabilitar(admin);

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        assertThat(userRepo.findByUsername("nuevo_dueno")).as("no se debe haber creado el dueno").isEmpty();
    }

    @Test
    void elDuenoConPasswordTemporalDeshabilitadoTampocoPuedeUsarSuTokenAcotado() throws Exception {
        String token = bearer(duenoPendiente);
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isOk()); // control

        deshabilitar(duenoPendiente);

        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(put(PERFIL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"OtraClave789!\"}")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- alcance y efectos colaterales ----------

    @Test
    void deshabilitarUnaCuentaNoAfectaALasDemas() throws Exception {
        String tokenA = bearer(compradorA);
        String tokenB = bearer(compradorB);
        String tokenDueno = bearer(duenoCentral);

        deshabilitar(compradorA);

        mvc.perform(get(PERFIL).header(AUTHORIZATION, tokenA)).andExpect(status().isUnauthorized());
        mvc.perform(get(PERFIL).header(AUTHORIZATION, tokenB)).andExpect(status().isOk());
        mvc.perform(get(LIBRERIA_PROPIA).header(AUTHORIZATION, tokenDueno)).andExpect(status().isOk());
    }

    @Test
    void elRechazoEsInmediatoYNoEsperaALaExpiracionDelToken() throws Exception {
        // El token de un comprador vive hasta 60 min: el rechazo no puede depender de que venza
        String token = bearer(compradorA);
        deshabilitar(compradorA);

        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUnaCuentaDeshabilitadaEnUnEndpointPublicoSeTrataComoAnonimo() throws Exception {
        // Igual que un token revocado o invalido: no rompe el catalogo publico,
        // solo deja de identificar a la cuenta
        String token = bearer(compradorA);
        deshabilitar(compradorA);

        mvc.perform(get("/api/books").header(AUTHORIZATION, token)).andExpect(status().isOk());
    }

    @Test
    void laCuentaDeshabilitadaNoPuedeCerrarSesionConSuTokenPeroElTokenYaEsInutil() throws Exception {
        // Documenta el comportamiento: al no autenticarse, el logout responde 401.
        // No es un problema: el token ya no sirve para nada, con o sin revocacion
        String token = bearer(compradorA);
        deshabilitar(compradorA);

        mvc.perform(post(LOGOUT).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    // ---------- recorrido real con login ----------

    @Test
    void recorridoRealLoginDeshabilitacionYNuevoLoginRechazado() throws Exception {
        // Token emitido por /login (no por JwtService directo): es el flujo del usuario real
        String token = "Bearer " + loginYObtenerToken("comprador_a");
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isOk());

        deshabilitar(compradorA);

        // 1) el token que ya tenia deja de servir...
        mvc.perform(get(PERFIL).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
        // 2) ...y no puede obtener uno nuevo, ni siquiera con la contrasena correcta
        String respuesta = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(credenciales("comprador_a")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).as("la respuesta de error no debe traer un token").doesNotContain("\"token\"");
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