package librosbuysan.user;

import static librosbuysan.support.HttpAssertions.assertThrowsStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Optional;
import librosbuysan.auth.service.PasswordHasher;
import librosbuysan.auth.service.TokenRevocationService;
import librosbuysan.security.AuthenticatedUser;
import librosbuysan.user.entity.User;
import librosbuysan.user.repository.UserRepo;
import librosbuysan.user.service.UserService;
import librosbuysan.user.dto.UserDtos.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cambio de contrasena (perfil y panel de admin), con foco en R18: la
 * contrasena temporal de un dueno creado por el admin.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;
    private static final String TEMPORAL = "TempPass23456789";
    private static final String NUEVA = "NuevaClave456!";
    private static final Instant EXPIRA = Instant.parse("2030-01-01T00:00:00Z");
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final PasswordHasher HASHER = new PasswordHasher();

    // bcrypt costo 12 es lento: se calcula una sola vez
    private static String hashTemporal;

    @Mock private UserRepo userRepo;
    @Mock private TokenRevocationService revocationService;

    private UserService service;

    @BeforeAll
    static void calcularHash() {
        hashTemporal = HASHER.hash(TEMPORAL.toCharArray());
    }

    @BeforeEach
    void setUp() {
        service = new UserService(userRepo, VALIDATOR, HASHER, revocationService);
    }

    private User guardar(User user) {
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private static UpdateProfileRequest cambio(String actual, String nueva) {
        return new UpdateProfileRequest(actual.toCharArray(), nueva.toCharArray());
    }

    @Test
    void elDuenoConPasswordTemporalLaCambiaYSuTokenSeRevoca() {
        User pendiente = guardar(User.duenoConPasswordTemporal("nuevo", "nuevo@test.local", hashTemporal));
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        var principal = new AuthenticatedUser(USER_ID, User.Role.DUENO, "jti-temporal", EXPIRA, true);

        service.updateProfile(principal, cambio(TEMPORAL, NUEVA));

        assertThat(pendiente.isMustChangePassword()).isFalse();
        assertThat(HASHER.matches(NUEVA.toCharArray(), pendiente.getPasswordHash())).isTrue();
        verify(revocationService).revoke("jti-temporal", EXPIRA);
    }

    @Test
    void unCambioNormalNoRevocaElToken() {
        guardar(User.dueno("dueno", "dueno@test.local", hashTemporal));
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());

        service.updateProfile(new AuthenticatedUser(USER_ID, User.Role.DUENO, "jti", EXPIRA), cambio(TEMPORAL, NUEVA));

        verifyNoInteractions(revocationService);
    }

    @Test
    void elAdminCambiaSuPasswordConLaActual() {
        User admin = guardar(User.admin("root", "root@test.local", hashTemporal));
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());

        service.updateProfile(new AuthenticatedUser(USER_ID, User.Role.ADMIN, "jti", EXPIRA), cambio(TEMPORAL, NUEVA));

        assertThat(HASHER.matches(NUEVA.toCharArray(), admin.getPasswordHash())).isTrue();
        verifyNoInteractions(revocationService);
    }

    @Test
    void laNuevaNoPuedeSerIgualALaTemporal() {
        User pendiente = guardar(User.duenoConPasswordTemporal("nuevo", "nuevo@test.local", hashTemporal));
        var principal = new AuthenticatedUser(USER_ID, User.Role.DUENO, "jti-temporal", EXPIRA, true);

        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.updateProfile(principal, cambio(TEMPORAL, TEMPORAL)));

        assertThat(pendiente.isMustChangePassword()).isTrue();
        assertThat(pendiente.getPasswordHash()).isEqualTo(hashTemporal);
        verify(userRepo, never()).saveAndFlush(any());
        verifyNoInteractions(revocationService);
    }

    @Test
    void conLaActualIncorrectaNoCambiaNadaNiRevocaElToken() {
        User pendiente = guardar(User.duenoConPasswordTemporal("nuevo", "nuevo@test.local", hashTemporal));
        var principal = new AuthenticatedUser(USER_ID, User.Role.DUENO, "jti-temporal", EXPIRA, true);

        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.updateProfile(principal, cambio("incorrecta999", NUEVA)));

        assertThat(pendiente.isMustChangePassword()).isTrue();
        verify(userRepo, never()).saveAndFlush(any());
        verifyNoInteractions(revocationService);
    }

    @Test
    void sinPasswordActualDa400() {
        guardar(User.admin("root", "root@test.local", hashTemporal));
        var principal = new AuthenticatedUser(USER_ID, User.Role.ADMIN, "jti", EXPIRA);

        assertThrowsStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateProfile(principal, new UpdateProfileRequest(null, NUEVA.toCharArray())));
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void borraAmbasPasswordsDeMemoria() {
        guardar(User.duenoConPasswordTemporal("nuevo", "nuevo@test.local", hashTemporal));
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        char[] actual = TEMPORAL.toCharArray();
        char[] nueva = NUEVA.toCharArray();

        service.updateProfile(new AuthenticatedUser(USER_ID, User.Role.DUENO, "jti", EXPIRA, true),
                new UpdateProfileRequest(actual, nueva));

        assertThat(actual).containsOnly('\0');
        assertThat(nueva).containsOnly('\0');
    }
}
