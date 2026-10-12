package librosbuysan.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import librosbuysan.auth.PasswordHasher;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generaContrasenasDe16CaracteresDelAlfabetoPermitido() {
        for (int i = 0; i < 200; i++) {
            char[] password = generator.generate();

            assertThat(password).hasSize(TemporaryPasswordGenerator.LENGTH);
            for (char c : password) {
                assertThat(TemporaryPasswordGenerator.ALPHABET).contains(String.valueOf(c));
            }
        }
    }

    @Test
    void elAlfabetoNoTieneCaracteresQueSeConfundenAlDictarlos() {
        assertThat(TemporaryPasswordGenerator.ALPHABET).doesNotContain("0", "O", "o", "1", "l", "I");
    }

    @Test
    void cumpleLasReglasDeContrasenaDelSistema() {
        // El dueno la usa como "contrasena actual" al cambiarla: tiene que
        // pasar las mismas validaciones que una contrasena elegida por el usuario
        char[] password = generator.generate();

        assertThat(password.length).isBetween(8, 64);
        assertThat(PasswordHasher.isValidLength(password)).isTrue();
    }

    @Test
    void noRepiteContrasenas() {
        Set<String> generadas = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generadas.add(new String(generator.generate()));
        }

        assertThat(generadas).hasSize(1000);
    }
}
