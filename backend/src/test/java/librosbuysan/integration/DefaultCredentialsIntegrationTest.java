package librosbuysan.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import librosbuysan.book.seed.BookDataSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * R26: ningun componente debe operar con credenciales por defecto del
 * proveedor o framework (ej. admin/admin, root/root). AdminSeeder ya exige
 * ADMIN_USERNAME/EMAIL/INITIAL_PASSWORD explicitos y nunca cae a un valor por
 * defecto si faltan (ver AdminSeederTest/AdminSeederIntegrationTest); esta
 * clase prueba el otro lado, con un admin YA seedeado (para descartar que el
 * 401 sea simplemente "todavia no hay admin"): que pares de credenciales
 * tipicos por defecto no autentiquen contra el login real.
 */
@SpringBootTest(properties = {
        "app.admin.username=Admin_Seed",
        "app.admin.email=admin.seed@test.local",
        "app.admin.initial-password=AdminSeed123!"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DefaultCredentialsIntegrationTest {

    private static final String LOGIN = "/api/auth/login";

    @MockitoBean
    private BookDataSeeder bookDataSeeder;

    @Autowired private MockMvc mvc;

    @Test
    void controlPositivoElAdminSeedeadoConCredencialesExplicitasSiAutentica() throws Exception {
        // Si esto fallara, los tests de abajo no probarian nada: el 401
        // podria deberse a que el admin ni siquiera se creo
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_seed\",\"password\":\"AdminSeed123!\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}/{1} no autentica")
    @CsvSource({
            "admin, admin",
            "admin, admin123",
            "admin, password",
            "admin, 12345678",
            "admin, changeme",
            "administrator, administrator",
            "root, root",
            "postgres, postgres",
            "sa, ''",
            "test, test",
            "user, user",
            "guest, guest"
    })
    void credencialesPorDefectoConocidasNoAutentican(String username, String password) throws Exception {
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elUsuarioLiteralAdminNoExiste() throws Exception {
        // El username seedeado es "admin_seed", no "admin": ni siquiera el
        // literal mas tipico de credencial por defecto llega a existir
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"CualquieraValida123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
