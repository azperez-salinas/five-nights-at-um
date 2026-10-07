package librosbuysan.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    // ---------- hash / matches ----------

    @Test
    void elHashVerificaConLaMismaPasswordYFallaConOtra() {
        String hash = hasher.hash("ClaveSegura123".toCharArray());

        assertThat(hasher.matches("ClaveSegura123".toCharArray(), hash)).isTrue();
        assertThat(hasher.matches("ClaveSegura124".toCharArray(), hash)).isFalse();
        assertThat(hasher.matches("".toCharArray(), hash)).isFalse();
    }

    @Test
    void elHashEsBcryptYNoContieneLaPasswordEnClaro() {
        String hash = hasher.hash("ClaveSegura123".toCharArray());

        assertThat(hash).startsWith("$2").doesNotContain("ClaveSegura123");
    }

    @Test
    void laMismaPasswordGeneraHashesDistintosPorLaSalAleatoria() {
        char[] clave = "ClaveSegura123".toCharArray();

        assertThat(hasher.hash(clave)).isNotEqualTo(hasher.hash(clave));
    }

    @Test
    void aceptaPasswordsConCaracteresNoAscii() {
        char[] clave = "contrase\u00f1a-\u20ac-\uD83D\uDE00".toCharArray();
        String hash = hasher.hash(clave);

        assertThat(hasher.matches(clave, hash)).isTrue();
    }

    // ---------- utf8Length ----------

    @Test
    void utf8LengthCuentaUnByteParaAscii() {
        assertThat(PasswordHasher.utf8Length("abc123".toCharArray())).isEqualTo(6);
    }

    @Test
    void utf8LengthCuentaDosBytesParaLatin1() {
        assertThat(PasswordHasher.utf8Length("\u00f1".toCharArray())).isEqualTo(2);
    }

    @Test
    void utf8LengthCuentaTresBytesParaElEuro() {
        assertThat(PasswordHasher.utf8Length("\u20ac".toCharArray())).isEqualTo(3);
    }

    @Test
    void utf8LengthCuentaCuatroBytesParaUnParDeSurrogates() {
        assertThat(PasswordHasher.utf8Length("\uD83D\uDE00".toCharArray())).isEqualTo(4);
    }

    @Test
    void utf8LengthDeVacioEsCero() {
        assertThat(PasswordHasher.utf8Length(new char[0])).isZero();
    }

    @Test
    void utf8LengthDevuelveMenosUnoParaSurrogateAltoSuelto() {
        assertThat(PasswordHasher.utf8Length(new char[] {'a', '\uD83D'})).isEqualTo(-1);
        assertThat(PasswordHasher.utf8Length(new char[] {'\uD83D', 'a'})).isEqualTo(-1);
    }

    @Test
    void utf8LengthDevuelveMenosUnoParaSurrogateBajoSuelto() {
        assertThat(PasswordHasher.utf8Length(new char[] {'\uDE00'})).isEqualTo(-1);
    }

    // ---------- isValidLength (limite de 72 bytes de bcrypt) ----------

    @Test
    void isValidLengthAceptaHasta72Bytes() {
        assertThat(PasswordHasher.isValidLength("a".repeat(72).toCharArray())).isTrue();
        assertThat(PasswordHasher.isValidLength("\u00f1".repeat(36).toCharArray())).isTrue();
    }

    @Test
    void isValidLengthRechazaMasDe72Bytes() {
        assertThat(PasswordHasher.isValidLength("a".repeat(73).toCharArray())).isFalse();
        assertThat(PasswordHasher.isValidLength("\u00f1".repeat(37).toCharArray())).isFalse();
    }

    @Test
    void isValidLengthRechazaUtf16Invalido() {
        assertThat(PasswordHasher.isValidLength(new char[] {'\uD83D'})).isFalse();
    }
}
