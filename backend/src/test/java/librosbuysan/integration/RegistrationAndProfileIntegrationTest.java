package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import librosbuysan.user.User;
import org.junit.jupiter.api.Test;
import librosbuysan.support.IntegrationTestBase;
import org.springframework.http.MediaType;

/**
 * Registro, login y perfil: ademas de funcionar, no deben permitir que un
 * cliente se otorgue privilegios (RS25, RS36) ni ver/editar la cuenta de otro (R15).
 */
class RegistrationAndProfileIntegrationTest extends IntegrationTestBase {

    // ---------- registro: el cliente no elige su rol ----------

    @Test
    void registroPublicoIgnoraRolYEnabledEnviadosPorElCliente() throws Exception {
        String body = """
                {"username":"nuevo_usuario","email":"nuevo@test.local","password":"Password123!",
                 "role":"DUENO","enabled":false,"id":999}
                """;

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("COMPRADOR"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        User creado = userRepo.findByUsername("nuevo_usuario").orElseThrow();
        assertThat(creado.getRole()).isEqualTo(User.Role.COMPRADOR);
        assertThat(creado.isEnabled()).isTrue();
        assertThat(creado.getId()).isNotEqualTo(999L);
    }

    @Test
    void elTokenDelRegistroPermiteUsarFuncionesDeCompradorYNoDeDueno() throws Exception {
        String body = "{\"username\":\"recien_llegado\",\"email\":\"rl@test.local\",\"password\":\"Password123!\"}";
        String respuesta = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");

        mvc.perform(get("/api/favorites").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void registroDeDuenoCreaLaLibreriaHabilitadaYAsignaRolDueno() throws Exception {
        String body = "{\"username\":\"dueno_nuevo\",\"email\":\"dn@test.local\",\"password\":\"Password123!\","
                + "\"nombreLibrary\":\"Mi Libreria\"}";

        mvc.perform(post("/api/auth/register-dueno").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DUENO"));

        User dueno = userRepo.findByUsername("dueno_nuevo").orElseThrow();
        assertThat(libraryRepo.findAll())
                .anySatisfy(l -> {
                    assertThat(l.getNombre()).isEqualTo("Mi Libreria");
                    assertThat(l.isEnabled()).isTrue();
                });
        assertThat(dueno.getRole()).isEqualTo(User.Role.DUENO);
    }

    @Test
    void registroDuplicadoEs409() throws Exception {
        String body = "{\"username\":\"comprador_a\",\"email\":\"otro@test.local\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void registroConDatosInvalidosEs400() throws Exception {
        String body = "{\"username\":\"x\",\"email\":\"no-es-mail\",\"password\":\"corta\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- login ----------

    @Test
    void loginCorrectoDevuelveTokenUsable() throws Exception {
        String body = "{\"username\":\"comprador_a\",\"password\":\"" + PASSWORD + "\"}";
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("COMPRADOR"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(respuesta, "$.token");
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("comprador_a"));
    }

    @Test
    void loginFallidoNoDistingueUsuarioInexistenteDePasswordIncorrecta() throws Exception {
        var passwordIncorrecta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"comprador_a\",\"password\":\"incorrecta999\"}")).andReturn().getResponse();
        var usuarioInexistente = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"no_existe\",\"password\":\"incorrecta999\"}")).andReturn().getResponse();

        assertThat(passwordIncorrecta.getStatus()).isEqualTo(401);
        assertThat(usuarioInexistente.getStatus()).isEqualTo(401);
        assertThat(usuarioInexistente.getErrorMessage()).isEqualTo(passwordIncorrecta.getErrorMessage());
    }

    // ---------- perfil: solo la propia cuenta ----------

    @Test
    void perfilDevuelveSiempreLaCuentaDelToken() throws Exception {
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("comprador_a"));
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(compradorB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("comprador_b"));
    }

    @Test
    void perfilIgnoraParametrosQueIntentanApuntarAOtraCuenta() throws Exception {
        mvc.perform(get("/api/users/me")
                        .param("id", String.valueOf(compradorB.getId()))
                        .param("userId", String.valueOf(compradorB.getId()))
                        .param("username", "comprador_b")
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("comprador_a"));
    }

    @Test
    void perfilNoExponeElHashDeLaPassword() throws Exception {
        mvc.perform(get("/api/users/me").header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void cambiarPasswordSoloModificaLaPasswordYNoElRolNiElUsername() throws Exception {
        String hashAntes = userRepo.findById(compradorA.getId()).orElseThrow().getPasswordHash();
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NuevaClave456!\","
                + "\"username\":\"hacker\",\"role\":\"DUENO\",\"email\":\"hacker@test.local\"}";

        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk());

        User despues = userRepo.findById(compradorA.getId()).orElseThrow();
        assertThat(despues.getPasswordHash()).isNotEqualTo(hashAntes);
        assertThat(despues.getUsername()).isEqualTo("comprador_a");
        assertThat(despues.getEmail()).isEqualTo("comprador_a@test.local");
        assertThat(despues.getRole()).isEqualTo(User.Role.COMPRADOR);
    }

    @Test
    void cambiarPasswordConLaActualIncorrectaEs400YNoModificaNada() throws Exception {
        String hashAntes = userRepo.findById(compradorA.getId()).orElseThrow().getPasswordHash();
        String body = "{\"currentPassword\":\"incorrecta999\",\"newPassword\":\"NuevaClave456!\"}";

        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isBadRequest());

        assertThat(userRepo.findById(compradorA.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashAntes);
    }

    @Test
    void cambiarPasswordDeUnoNoAfectaLaCuentaDeOtro() throws Exception {
        String hashBAntes = userRepo.findById(compradorB.getId()).orElseThrow().getPasswordHash();
        String body = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NuevaClave456!\"}";

        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(AUTHORIZATION, bearer(compradorA)))
                .andExpect(status().isOk());

        assertThat(userRepo.findById(compradorB.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashBAntes);
    }
}
