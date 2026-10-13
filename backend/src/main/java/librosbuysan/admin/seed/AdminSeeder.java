package librosbuysan.admin.seed;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import librosbuysan.auth.service.PasswordHasher;
import librosbuysan.user.entity.User;
import librosbuysan.user.repository.UserRepo;

/**
 * R18/F18: la cuenta de administrador se crea directamente en el sistema, no
 * existe endpoint ni formulario para crear admins.
 *
 * <p>Se crea una sola vez, cuando todavia no hay ningun ADMIN, con los datos
 * de ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_INITIAL_PASSWORD. Despues de eso las
 * variables se ignoran: la contrasena se cambia desde el panel. Si faltan,
 * no se crea nada (por ejemplo en los tests); si estan mal formadas, la
 * aplicacion no arranca, para no dejar un admin con datos invalidos.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    // Mismas reglas que el registro publico
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_EMAIL_CHARS = 254;
    private static final int MIN_PASSWORD_CHARS = 8;
    private static final int MAX_PASSWORD_CHARS = 64;

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final String username;
    private final String email;
    private final String initialPassword;

    public AdminSeeder(UserRepo userRepo, PasswordHasher passwordHasher,
                       @Value("${app.admin.username:}") String username,
                       @Value("${app.admin.email:}") String email,
                       @Value("${app.admin.initial-password:}") String initialPassword) {
        this.userRepo = userRepo;
        this.passwordHasher = passwordHasher;
        this.username = username;
        this.email = email;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepo.existsByRole(User.Role.ADMIN)) {
            log.info("Cuenta de administrador ya existente, se omite su creacion.");
            return;
        }
        if (isBlank(username) || isBlank(email) || isBlank(initialPassword)) {
            log.warn("No se creo la cuenta de administrador: faltan ADMIN_USERNAME, ADMIN_EMAIL "
                    + "o ADMIN_INITIAL_PASSWORD.");
            return;
        }

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        // Los mensajes nunca incluyen la contrasena
        if (!USERNAME.matcher(normalizedUsername).matches()) {
            throw new IllegalStateException("ADMIN_USERNAME invalido: 3 a 30 caracteres (letras, numeros o _)");
        }
        if (normalizedEmail.length() > MAX_EMAIL_CHARS || !EMAIL.matcher(normalizedEmail).matches()) {
            throw new IllegalStateException("ADMIN_EMAIL invalido");
        }
        if (userRepo.existsByUsername(normalizedUsername) || userRepo.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("ADMIN_USERNAME o ADMIN_EMAIL ya pertenecen a otra cuenta");
        }

        char[] password = initialPassword.toCharArray();
        try {
            if (password.length < MIN_PASSWORD_CHARS || password.length > MAX_PASSWORD_CHARS
                    || !PasswordHasher.isValidLength(password)) {
                throw new IllegalStateException("ADMIN_INITIAL_PASSWORD invalida: 8 a 64 caracteres");
            }
            userRepo.saveAndFlush(User.admin(normalizedUsername, normalizedEmail, passwordHasher.hash(password)));
            log.info("Cuenta de administrador creada: username={}", normalizedUsername);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
