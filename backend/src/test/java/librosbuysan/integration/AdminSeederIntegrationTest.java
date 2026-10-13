package librosbuysan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.admin.seed.AdminSeeder;
import librosbuysan.book.seed.BookDataSeeder;
import librosbuysan.user.entity.User;
import librosbuysan.user.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * R18/F18: al arrancar la aplicacion con ADMIN_* configurados, la cuenta de
 * admin queda creada (seed) y puede iniciar sesion por el login comun.
 */
@SpringBootTest(properties = {
        "app.admin.username=Admin_Seed",
        "app.admin.email=admin.seed@test.local",
        "app.admin.initial-password=AdminSeed123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSeederIntegrationTest {

    @MockitoBean
    private BookDataSeeder bookDataSeeder;

    @Autowired private MockMvc mvc;
    @Autowired private UserRepo userRepo;
    @Autowired private AdminSeeder adminSeeder;

    @Test
    void alArrancarSeCreaElAdminYPuedeIniciarSesion() throws Exception {
        User admin = userRepo.findByUsername("admin_seed").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(admin.getEmail()).isEqualTo("admin.seed@test.local");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_seed\",\"password\":\"AdminSeed123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void volverAEjecutarElSeedNoCreaUnSegundoAdmin() {
        long admins = userRepo.findAll().stream().filter(u -> u.getRole() == User.Role.ADMIN).count();

        adminSeeder.run();

        assertThat(userRepo.findAll().stream().filter(u -> u.getRole() == User.Role.ADMIN).count())
                .isEqualTo(admins).isEqualTo(1);
    }
}
