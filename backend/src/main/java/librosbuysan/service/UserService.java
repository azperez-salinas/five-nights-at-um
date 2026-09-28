package librosbuysan.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.auth.PasswordHasher;
import librosbuysan.user.UserDtos.ProfileResponse;
import librosbuysan.user.UserDtos.UpdateProfileRequest;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String INVALID_REQUEST = "Solicitud invalida";
    private static final String NOTHING_TO_UPDATE = "No hay cambios para aplicar";
    private static final String WRONG_CURRENT_PASSWORD = "La contrasena actual es incorrecta";
    private static final String USERNAME_TAKEN = "El nombre de usuario ya esta en uso";
    private static final String UNAUTHORIZED = "No autenticado";

    private final UserRepo userRepo;
    private final Validator validator;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepo userRepo, Validator validator, PasswordHasher passwordHasher) {
        this.userRepo = userRepo;
        this.validator = validator;
        this.passwordHasher = passwordHasher;
    }

    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(loadActiveUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        char[] currentPassword = request == null ? null : request.currentPassword();
        char[] newPassword = request == null ? null : request.newPassword();
        try {
            validate(request);

            User user = loadActiveUser(userId);

            if (newPassword != null) {
                applyPasswordChange(user, currentPassword, newPassword);
            }
            if (request.username() != null) {
                applyUsernameChange(user, request.username());
            }

            User saved = save(user);
            log.info("Perfil actualizado: userId={}", userId);
            return ProfileResponse.from(saved);
        } finally {
            if (currentPassword != null) {
                Arrays.fill(currentPassword, '\0');
            }
            if (newPassword != null) {
                Arrays.fill(newPassword, '\0');
            }
        }
    }

    private void applyPasswordChange(User user, char[] currentPassword, char[] newPassword) {
        if (currentPassword == null || currentPassword.length == 0) {
            log.warn("Actualizacion de perfil fallida: userId={} motivo=falta_password_actual", user.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        if (!PasswordHasher.isValidLength(newPassword)) {
            log.warn("Actualizacion de perfil fallida: userId={} motivo=password_nueva_invalida", user.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Actualizacion de perfil fallida: userId={} motivo=password_actual_incorrecta", user.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, WRONG_CURRENT_PASSWORD);
        }
        user.changePasswordHash(passwordHasher.hash(newPassword));
    }

    private void applyUsernameChange(User user, String rawUsername) {
        String username = normalize(rawUsername);
        if (username.equals(user.getUsername())) {
            return;
        }
        if (userRepo.existsByUsername(username)) {
            log.warn("Actualizacion de perfil fallida: userId={} motivo=username_duplicado", user.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, USERNAME_TAKEN);
        }
        user.changeUsername(username);
    }

    private User save(User user) {
        try {
            return userRepo.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Actualizacion de perfil fallida: userId={} motivo=username_duplicado_concurrente", user.getId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, USERNAME_TAKEN);
        }
    }

    private User loadActiveUser(Long userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            log.warn("Acceso a perfil rechazado: userId={} motivo=usuario_inexistente_o_deshabilitado", userId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, UNAUTHORIZED);
        }
        return user;
    }

    private void validate(UpdateProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
        if (request.username() == null && request.newPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOTHING_TO_UPDATE);
        }
        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}