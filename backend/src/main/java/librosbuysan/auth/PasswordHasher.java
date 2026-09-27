package librosbuysan.auth;

import java.security.SecureRandom;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private static final int BCRYPT_COST = 12;
    private static final int BCRYPT_SALT_BYTES = 16;
    public static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(char[] password) {
        byte[] salt = new byte[BCRYPT_SALT_BYTES];
        secureRandom.nextBytes(salt);
        return OpenBSDBCrypt.generate(password, salt, BCRYPT_COST);
    }

    public boolean matches(char[] password, String hash) {
        return OpenBSDBCrypt.checkPassword(hash, password);
    }

    public static int utf8Length(char[] chars) {
        int length = 0;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c < 0x80) {
                length += 1;
            } else if (c < 0x800) {
                length += 2;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= chars.length || !Character.isLowSurrogate(chars[i + 1])) {
                    return -1;
                }
                length += 4;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return -1;
            } else {
                length += 3;
            }
        }
        return length;
    }

    public static boolean isValidLength(char[] password) {
        int bytes = utf8Length(password);
        return bytes >= 0 && bytes <= BCRYPT_MAX_PASSWORD_BYTES;
    }
}