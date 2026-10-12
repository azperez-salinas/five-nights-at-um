package librosbuysan.admin;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * R18: contrasenas temporales para los duenos que crea el admin.
 *
 * <p>El admin se la entrega al dueno a mano (la dicta o la copia), asi que el
 * alfabeto excluye caracteres que se confunden entre si (0/O/o, 1/l/I).
 * 16 caracteres de un alfabeto de 54 dan ~92 bits de entropia.
 */
@Component
public class TemporaryPasswordGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    static final int LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public char[] generate() {
        char[] password = new char[LENGTH];
        for (int i = 0; i < password.length; i++) {
            password[i] = ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length()));
        }
        return password;
    }
}
