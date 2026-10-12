package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import librosbuysan.auth.PasswordHasher;
import librosbuysan.library.Library;
import librosbuysan.support.IntegrationTestBase;
import librosbuysan.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * R18/F18: panel de administrador, de punta a punta y con la cadena de
 * seguridad REAL (tokens emitidos por el login, no @WithMockUser).
 */
class AdminIntegrationTest extends IntegrationTestBase {

    private static final String OWNERS = "/api/admin/owners";
    private static final String ADMIN_PASSWORD = "/api/admin/password";
    private static final String LOGIN = "/api/auth/login";
    private static final String ME = "/api/users/me";
    private static final String NUEVA = "NuevaClave456!";

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static String token(String loginResponse) {
        return "Bearer " + JsonPath.read(loginResponse, "$.token");
    }

    private String crearDueno(String username, String email, String libreria) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + email + "\","
                + "\"nombreLibreria\":\"" + libreria + "\"}";
        return mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private static String cambio(String actual, String nueva) {
        return "{\"currentPassword\":\"" + actual + "\",\"newPassword\":\"" + nueva + "\"}";
    }

    // ---------- login del admin ----------

    @Test
    void elAdminIniciaSesionPorElLoginComunYRecibeElRolAdmin() throws Exception {
        String respuesta = login("admin_test", PASSWORD);

        assertThat((String) JsonPath.read(respuesta, "$.role")).isEqualTo("ADMIN");
        assertThat((Boolean) JsonPath.read(respuesta, "$.mustChangePassword")).isFalse();

        // El token del login sirve para las acciones del panel...
        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(AUTHORIZATION, token(respuesta)))
                .andExpect(status().isBadRequest());
        // ...y para nada mas
        mvc.perform(get(ME).header(AUTHORIZATION, token(respuesta))).andExpect(status().isForbidden());
    }

    // ---------- alta de dueno ----------

    @Test
    void elAdminCreaUnDuenoConSuLibreriaYRecibeLaPasswordTemporalUnaSolaVez() throws Exception {
        String body = "{\"username\":\"Dueno_Sur\",\"email\":\"Sur@Test.Local\",\"nombreLibreria\":\"Libreria Sur\"}";

        String respuesta = mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.username").value("dueno_sur"))
                .andExpect(jsonPath("$.email").value("sur@test.local"))
                .andExpect(jsonPath("$.nombreLibreria").value("Libreria Sur"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String temporal = JsonPath.read(respuesta, "$.passwordTemporal");
        assertThat(temporal).hasSize(16);

        User dueno = userRepo.findByUsername("dueno_sur").orElseThrow();
        assertThat(dueno.getRole()).isEqualTo(User.Role.DUENO);
        assertThat(dueno.isMustChangePassword()).isTrue();
        assertThat(dueno.isEnabled()).isTrue();
        // En la base solo queda el hash
        assertThat(dueno.getPasswordHash()).isNotEqualTo(temporal).doesNotContain(temporal);
        assertThat(new PasswordHasher().matches(temporal.toCharArray(), dueno.getPasswordHash())).isTrue();

        Library libreria = libraryRepo.findByDuenoIdAndEnabledTrue(dueno.getId()).orElseThrow();
        assertThat(libreria.getNombre()).isEqualTo("Libreria Sur");
        assertThat(((Number) JsonPath.read(respuesta, "$.libreriaId")).longValue()).isEqualTo(libreria.getId());
    }

    @Test
    void cadaDuenoRecibeUnaPasswordTemporalDistinta() throws Exception {
        String a = JsonPath.read(crearDueno("dueno_uno", "uno@test.local", "Libreria Uno"), "$.passwordTemporal");
        String b = JsonPath.read(crearDueno("dueno_dos", "dos@test.local", "Libreria Dos"), "$.passwordTemporal");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void elRolEnviadoEnLaSolicitudSeIgnoraYSiempreSeCreaUnDueno() throws Exception {
        String body = """
                {"username":"quiere_ser_admin","email":"qsa@test.local","nombreLibreria":"Libreria X",
                 "role":"ADMIN","enabled":false,"mustChangePassword":false,"password":"Elegida123!","id":1}
                """;

        String respuesta = mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        User creado = userRepo.findByUsername("quiere_ser_admin").orElseThrow();
        assertThat(creado.getRole()).isEqualTo(User.Role.DUENO);
        assertThat(creado.isEnabled()).isTrue();
        assertThat(creado.isMustChangePassword()).isTrue();
        assertThat(creado.getId()).isNotEqualTo(1L);
        // La contrasena tampoco la elige el cliente
        assertThat(new PasswordHasher().matches("Elegida123!".toCharArray(), creado.getPasswordHash())).isFalse();
        assertThat((String) JsonPath.read(respuesta, "$.passwordTemporal")).isNotEqualTo("Elegida123!");
    }

    @Test
    void usernameOEmailDuplicadoDa409YNoCreaLibreria() throws Exception {
        long librerias = libraryRepo.count();

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"comprador_a\",\"email\":\"nuevo@test.local\",\"nombreLibreria\":\"L1\"}")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());
        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo\",\"email\":\"COMPRADOR_A@test.local\",\"nombreLibreria\":\"L1\"}")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict());

        assertThat(libraryRepo.count()).isEqualTo(librerias);
        assertThat(userRepo.findByUsername("nuevo")).isEmpty();
    }

    @Test
    void datosInvalidosDan400() throws Exception {
        for (String body : new String[] {
                "{}",
                "{\"username\":\"x\",\"email\":\"x@test.local\",\"nombreLibreria\":\"Libreria\"}",
                "{\"username\":\"valido\",\"email\":\"no-es-mail\",\"nombreLibreria\":\"Libreria\"}",
                "{\"username\":\"valido\",\"email\":\"v@test.local\",\"nombreLibreria\":\" \"}",
                "{\"username\":\"valido\",\"email\":\"v@test.local\"}"}) {
            mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(userRepo.findByUsername("valido")).isEmpty();
    }

    @Test
    void ningunOtroRolPuedeCrearDuenosNiSiquieraUnDueno() throws Exception {
        String body = "{\"username\":\"colado\",\"email\":\"colado@test.local\",\"nombreLibreria\":\"Libreria\"}";

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (User usuario : new User[] {compradorA, duenoCentral, duenoPendiente}) {
            mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(AUTHORIZATION, bearer(usuario)))
                    .andExpect(status().isForbidden());
        }

        assertThat(userRepo.findByUsername("colado")).isEmpty();
    }

    // ---------- primer inicio de sesion del dueno ----------

    @Test
    void flujoCompletoElDuenoEntraConLaTemporalLaCambiaYRecienAhiUsaSuRol() throws Exception {
        String temporal = JsonPath.read(crearDueno("dueno_flujo", "flujo@test.local", "Libreria Flujo"),
                "$.passwordTemporal");

        // 1. Login con la temporal: avisa que debe cambiarla
        String primerLogin = login("dueno_flujo", temporal);
        assertThat((String) JsonPath.read(primerLogin, "$.role")).isEqualTo("DUENO");
        assertThat((Boolean) JsonPath.read(primerLogin, "$.mustChangePassword")).isTrue();
        String tokenTemporal = token(primerLogin);

        // 2. Con ese token NO puede usar funciones de dueno (aunque se saltee el frontend)
        mvc.perform(get("/api/users/libreria").header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/books/import").header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isForbidden());
        // ...pero si ver su perfil
        mvc.perform(get(ME).header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("dueno_flujo"));

        // 3. Cambia la contrasena exigiendo la temporal como actual
        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(cambio(temporal, NUEVA))
                        .header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isOk());

        // 4. El token temporal queda revocado
        mvc.perform(get(ME).header(AUTHORIZATION, tokenTemporal)).andExpect(status().isUnauthorized());

        // 5. La temporal ya no sirve; la nueva si, y con el rol completo
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dueno_flujo\",\"password\":\"" + temporal + "\"}"))
                .andExpect(status().isUnauthorized());
        String segundoLogin = login("dueno_flujo", NUEVA);
        assertThat((Boolean) JsonPath.read(segundoLogin, "$.mustChangePassword")).isFalse();
        mvc.perform(get("/api/users/libreria").header(AUTHORIZATION, token(segundoLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Libreria Flujo"));

        assertThat(userRepo.findByUsername("dueno_flujo").orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void elDuenoNoPuedeReemplazarLaTemporalPorLaMismaPassword() throws Exception {
        String temporal = JsonPath.read(crearDueno("dueno_igual", "igual@test.local", "Libreria Igual"),
                "$.passwordTemporal");
        String tokenTemporal = token(login("dueno_igual", temporal));

        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(cambio(temporal, temporal))
                        .header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isBadRequest());

        assertThat(userRepo.findByUsername("dueno_igual").orElseThrow().isMustChangePassword()).isTrue();
        // El token sigue sirviendo para intentarlo de nuevo
        mvc.perform(get(ME).header(AUTHORIZATION, tokenTemporal)).andExpect(status().isOk());
    }

    @Test
    void conLaTemporalIncorrectaNoSeCambiaNada() throws Exception {
        String temporal = JsonPath.read(crearDueno("dueno_error", "error@test.local", "Libreria Error"),
                "$.passwordTemporal");
        String tokenTemporal = token(login("dueno_error", temporal));

        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(cambio("incorrecta999", NUEVA))
                        .header(AUTHORIZATION, tokenTemporal))
                .andExpect(status().isBadRequest());

        assertThat(userRepo.findByUsername("dueno_error").orElseThrow().isMustChangePassword()).isTrue();
        login("dueno_error", temporal);
    }

    @Test
    void elDuenoPendientePuedeCerrarSesion() throws Exception {
        String token = bearer(duenoPendiente);

        mvc.perform(post("/api/auth/logout").header(AUTHORIZATION, token)).andExpect(status().isNoContent());
        mvc.perform(get(ME).header(AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    // ---------- cambio de contrasena del admin ----------

    @Test
    void elAdminCambiaSuPasswordExigiendoLaActual() throws Exception {
        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content(cambio(PASSWORD, NUEVA))
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat((String) JsonPath.read(login("admin_test", NUEVA), "$.role")).isEqualTo("ADMIN");
    }

    @Test
    void elAdminNoCambiaSuPasswordSinLaActualCorrecta() throws Exception {
        String hashAntes = userRepo.findById(admin.getId()).orElseThrow().getPasswordHash();

        for (String body : new String[] {
                cambio("incorrecta999", NUEVA),
                "{\"newPassword\":\"" + NUEVA + "\"}",
                "{\"currentPassword\":\"\",\"newPassword\":\"" + NUEVA + "\"}",
                cambio(PASSWORD, "corta"),
                "{\"currentPassword\":\"" + PASSWORD + "\"}"}) {
            mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content(body)
                            .header(AUTHORIZATION, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        assertThat(userRepo.findById(admin.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashAntes);
    }

    @Test
    void elCambioDePasswordDelAdminNoAceptaOtrosCampos() throws Exception {
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NUEVA + "\","
                + "\"username\":\"otro\",\"role\":\"COMPRADOR\",\"email\":\"otro@test.local\"}";

        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        User despues = userRepo.findById(admin.getId()).orElseThrow();
        assertThat(despues.getUsername()).isEqualTo("admin_test");
        assertThat(despues.getEmail()).isEqualTo("admin_test@test.local");
        assertThat(despues.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void unDuenoNoPuedeUsarElCambioDePasswordDelAdmin() throws Exception {
        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content(cambio(PASSWORD, NUEVA))
                        .header(AUTHORIZATION, bearer(duenoCentral)))
                .andExpect(status().isForbidden());
    }

    // ---------- el admin no tiene ningun otro permiso ----------

    @Test
    void elAdminRecibe403EnTodaOperacionProtegidaQueNoEsDelPanel() throws Exception {
        String token = bearer(admin);

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/favorites/" + libroCentral.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(get(ME).header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(put(ME).contentType(MediaType.APPLICATION_JSON).content(cambio(PASSWORD, NUEVA))
                        .header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/libreria").header(AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/libreria/" + central.getId()).header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/books/import").header(AUTHORIZATION, token)).andExpect(status().isForbidden());

        // Ninguna de esas llamadas tuvo efecto
        assertThat(libraryRepo.findById(central.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(favoriteRepo.count()).isZero();
    }

    @Test
    void elAdminPuedeCerrarSesion() throws Exception {
        String token = bearer(admin);

        mvc.perform(post("/api/auth/logout").header(AUTHORIZATION, token)).andExpect(status().isNoContent());
        mvc.perform(put(ADMIN_PASSWORD).contentType(MediaType.APPLICATION_JSON).content(cambio(PASSWORD, NUEVA))
                        .header(AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFalsificadoConRolAdminNoSirve() throws Exception {
        // Firmado con otra clave: el filtro lo descarta y la ruta pide autenticacion
        String forjado = Jwts.builder()
                .subject(String.valueOf(compradorA.getId()))
                .claim("role", "ADMIN")
                .id("jti-forjado")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(
                        "otra-clave-de-32-bytes-o-mas-123456".getBytes()), Jwts.SIG.HS256)
                .compact();

        mvc.perform(post(OWNERS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"forjado\",\"email\":\"f@test.local\",\"nombreLibreria\":\"L\"}")
                        .header(AUTHORIZATION, "Bearer " + forjado))
                .andExpect(status().isUnauthorized());
        assertThat(userRepo.findByUsername("forjado")).isEmpty();
    }
}
