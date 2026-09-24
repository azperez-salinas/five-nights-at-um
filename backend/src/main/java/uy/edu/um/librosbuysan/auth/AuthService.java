package uy.edu.um.librosbuysan.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uy.edu.um.librosbuysan.auth.AuthDtos.AuthResponse;
import uy.edu.um.librosbuysan.auth.AuthDtos.LoginRequest;
import uy.edu.um.librosbuysan.auth.AuthDtos.RegisterRequest;
import uy.edu.um.librosbuysan.user.User;
import uy.edu.um.librosbuysan.user.UserRepository;

/**
 * Registro (R1 + R8) y login (R2).
 *
 * <p>La contrasena se maneja siempre como char[] y nunca se convierte a String.
 * Todo el cuerpo de register y login esta dentro de un try/finally que la
 * sobrescribe, asi se borra en todos los caminos: request invalido, duplicado,
 * credenciales incorrectas o excepcion inesperada.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int BCRYPT_COST = 12;
    private static final int BCRYPT_SALT_BYTES = 16;
    // BCrypt solo usa los primeros 72 bytes de la contrasena
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final int DUMMY_PASSWORD_CHARS = 32;
    private static final int MAX_LOGGED_USERNAME_CHARS = 30;

    private static final String INVALID_REQUEST = "Solicitud invalida";
    private static final String ALREADY_REGISTERED = "El usuario o el email ya estan registrados";
    private static final String INVALID_CREDENTIALS = "Credenciales invalidas";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final Validator validator;
    private final SecureRandom secureRandom = new SecureRandom();

    // Hash contra el que se verifica cuando el usuario no existe, para que el
    // tiempo de respuesta no permita enumerar usuarios
    private final String dummyHash;

    public AuthService(UserRepository userRepository, JwtService jwtService, Validator validator) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.validator = validator;
        this.dummyHash = createDummyHash();
    }

    public AuthResponse register(RegisterRequest request) {
        char[] password = request == null ? null : request.password();
        try {
            validate(request, request == null ? null : request.username(), "Registro");

            String username = normalize(request.username());
            String email = normalize(request.email());

            if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
                log.warn("Registro fallido: username={} motivo=duplicado", username);
                throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
            }

            User user = new User(username, email, hashPassword(password));
            try {
                user = userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                // Otro registro con el mismo username/email se guardo entre el chequeo y el insert
                log.warn("Registro fallido: username={} motivo=duplicado_concurrente", username);
                throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
            }

            log.info("Registro exitoso: username={}", username);
            // R8: auto-login, el token va en la misma respuesta del registro
            return buildResponse(user);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    public AuthResponse login(LoginRequest request) {
        char[] password = request == null ? null : request.password();
        try {
            validate(request, request == null ? null : request.username(), "Login");

            String username = normalize(request.username());
            User user = userRepository.findByUsername(username).orElse(null);

            // Siempre se ejecuta un checkPassword, exista o no el usuario
            String hash = user != null ? user.getPasswordHash() : dummyHash;
            boolean matches = OpenBSDBCrypt.checkPassword(hash, password);

            if (user == null || !user.isEnabled() || !matches) {
                String reason = user == null ? "usuario_inexistente"
                        : !user.isEnabled() ? "usuario_deshabilitado" : "password_incorrecta";
                log.warn("Login fallido: username={} motivo={}", username, reason);
                // Mismo mensaje generico en los tres casos
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
            }

            log.info("Login exitoso: username={}", username);
            return buildResponse(user);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    /**
     * Valida el request con Bean Validation y ademas que la contrasena,
     * codificada en UTF-8, no supere los 72 bytes que usa BCrypt.
     */
    private void validate(Object request, String rawUsername, String operation) {
        if (request == null) {
            log.warn("{} fallido: username=- motivo=request_vacio", operation);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            log.warn("{} fallido: username={} motivo=request_invalido", operation, safeForLog(rawUsername));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        char[] password = request instanceof RegisterRequest r ? r.password() : ((LoginRequest) request).password();
        int utf8Bytes = utf8Length(password);
        if (utf8Bytes < 0 || utf8Bytes > BCRYPT_MAX_PASSWORD_BYTES) {
            log.warn("{} fallido: username={} motivo=password_invalida", operation, safeForLog(rawUsername));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
    }

    /**
     * Largo en bytes de la contrasena codificada en UTF-8, calculado recorriendo
     * el char[] sin crear un String ni una copia. Devuelve -1 si hay surrogates
     * sueltos (UTF-16 invalido), que BouncyCastle no puede codificar.
     */
    private static int utf8Length(char[] chars) {
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

    private String hashPassword(char[] password) {
        byte[] salt = new byte[BCRYPT_SALT_BYTES];
        secureRandom.nextBytes(salt);
        return OpenBSDBCrypt.generate(password, salt, BCRYPT_COST);
    }

    private String createDummyHash() {
        char[] dummy = new char[DUMMY_PASSWORD_CHARS];
        try {
            for (int i = 0; i < dummy.length; i++) {
                // ASCII imprimible: '!' (33) a '~' (126)
                dummy[i] = (char) ('!' + secureRandom.nextInt(94));
            }
            return hashPassword(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    private AuthResponse buildResponse(User user) {
        String token = jwtService.generateToken(user);
        long expiresIn = jwtService.getExpirationSeconds(user.getRole());
        return new AuthResponse(token, expiresIn, user.getUsername(), user.getRole());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    // Evita inyeccion de lineas en los logs cuando el username es invalido
    private static String safeForLog(String value) {
        if (value == null) {
            return "-";
        }
        String truncated = value.length() > MAX_LOGGED_USERNAME_CHARS
                ? value.substring(0, MAX_LOGGED_USERNAME_CHARS) + "..."
                : value;
        return truncated.replaceAll("\\p{Cntrl}", "?");
    }
}
