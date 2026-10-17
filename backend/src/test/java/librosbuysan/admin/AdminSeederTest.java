package librosbuysan.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import librosbuysan.auth.service.PasswordHasher;
import librosbuysan.user.entity.User;
import librosbuysan.user.repository.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import librosbuysan.admin.seed.AdminSeeder;

/**
 * R18/F18: la cuenta de admin se crea por seed y solo una vez.
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    private static final String PASSWORD = "AdminInicial123";

    @Mock private UserRepo userRepo;

    private final PasswordHasher passwordHasher = new PasswordHasher();

    private AdminSeeder seeder(String username, String email, String password) {
        return new AdminSeeder(userRepo, passwordHasher, username, email, password);
    }

    @Test
    void creaElAdminConLaPasswordHasheadaCuandoNoHayNinguno() {
        seeder(" Admin ", " Admin@Test.Local ", PASSWORD).run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).saveAndFlush(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getEmail()).isEqualTo("admin@test.local");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(passwordHasher.matches(PASSWORD.toCharArray(), admin.getPasswordHash())).isTrue();
    }

    @Test
    void siYaExisteUnAdminNoHaceNadaAunqueCambienLasVariables() {
        when(userRepo.existsByRole(User.Role.ADMIN)).thenReturn(true);

        seeder("otro_admin", "otro@test.local", "OtraPassword123").run();

        verify(userRepo, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL, admin@test.local, AdminInicial123",
            "admin, NULL, AdminInicial123",
            "admin, admin@test.local, NULL",
            "'  ', admin@test.local, AdminInicial123",
            "admin, admin@test.local, '   '"
    }, nullValues = "NULL")
    void siFaltaAlgunaVariableNoCreaNadaNiFallaElArranque(String username, String email, String password) {
        seeder(username, email, password).run();

        verify(userRepo, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({
            "ab, admin@test.local, AdminInicial123",
            "admin con espacio, admin@test.local, AdminInicial123",
            "admin, no-es-mail, AdminInicial123",
            "admin, admin@test.local, corta",
    })
    void conDatosInvalidosNoArrancaYNoMuestraLaPassword(String username, String email, String password) {
        assertThatThrownBy(() -> seeder(username, email, password).run())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(password));

        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void passwordDeMasDe64CaracteresEsInvalida() {
        String larga = "a".repeat(65);

        assertThatThrownBy(() -> seeder("admin", "admin@test.local", larga).run())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(larga));
    }

    @Test
    void siElUsernameOElEmailYaSonDeOtraCuentaNoArranca() {
        when(userRepo.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> seeder("admin", "admin@test.local", PASSWORD).run())
                .isInstanceOf(IllegalStateException.class);

        verify(userRepo, never()).saveAndFlush(any());
    }
}
