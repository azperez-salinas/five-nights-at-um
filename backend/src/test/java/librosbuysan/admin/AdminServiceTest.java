package librosbuysan.admin;

import static librosbuysan.support.HttpAssertions.assertThrowsStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.stream.Stream;
import librosbuysan.admin.AdminDtos.CreateOwnerRequest;
import librosbuysan.admin.AdminDtos.OwnerCreatedResponse;
import librosbuysan.auth.PasswordHasher;
import librosbuysan.library.Library;
import librosbuysan.library.LibraryRepo;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * R18/F18: alta de duenos por el admin.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String TEMPORAL = "TempPass23456789";

    @Mock private UserRepo userRepo;
    @Mock private LibraryRepo libraryRepo;
    @Mock private TemporaryPasswordGenerator passwordGenerator;

    private final PasswordHasher passwordHasher = new PasswordHasher();
    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(userRepo, libraryRepo, passwordHasher, passwordGenerator, VALIDATOR);
    }

    private void guardadoExitoso() {
        when(passwordGenerator.generate()).thenReturn(TEMPORAL.toCharArray());
        when(userRepo.saveAndFlush(any(User.class))).then(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });
        when(libraryRepo.saveAndFlush(any(Library.class))).then(returnsFirstArg());
    }

    @Test
    void creaUnDuenoConPasswordTemporalHasheadaYSuLibreria() {
        guardadoExitoso();

        OwnerCreatedResponse response = service.createOwner(ADMIN_ID,
                new CreateOwnerRequest("NuevoDueno", "Dueno@Test.Local", "  Libreria Sur  "));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).saveAndFlush(userCaptor.capture());
        User guardado = userCaptor.getValue();
        assertThat(guardado.getUsername()).isEqualTo("nuevodueno");
        assertThat(guardado.getEmail()).isEqualTo("dueno@test.local");
        assertThat(guardado.getRole()).isEqualTo(User.Role.DUENO);
        assertThat(guardado.isEnabled()).isTrue();
        assertThat(guardado.isMustChangePassword()).isTrue();
        // Se guarda solo el hash, y corresponde a la temporal que se devuelve
        assertThat(guardado.getPasswordHash()).startsWith("$2").doesNotContain(TEMPORAL);
        assertThat(passwordHasher.matches(TEMPORAL.toCharArray(), guardado.getPasswordHash())).isTrue();

        ArgumentCaptor<Library> libraryCaptor = ArgumentCaptor.forClass(Library.class);
        verify(libraryRepo).saveAndFlush(libraryCaptor.capture());
        assertThat(libraryCaptor.getValue().getNombre()).isEqualTo("Libreria Sur");
        assertThat(libraryCaptor.getValue().getDueno()).isSameAs(guardado);
        assertThat(libraryCaptor.getValue().isEnabled()).isTrue();

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("nuevodueno");
        assertThat(response.email()).isEqualTo("dueno@test.local");
        assertThat(response.nombreLibreria()).isEqualTo("Libreria Sur");
        assertThat(new String(response.passwordTemporal())).isEqualTo(TEMPORAL);
    }

    @Test
    void elToStringDeLaRespuestaNoMuestraLaPasswordTemporal() {
        guardadoExitoso();

        OwnerCreatedResponse response = service.createOwner(ADMIN_ID,
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "Libreria Sur"));

        assertThat(response.toString()).doesNotContain(TEMPORAL).contains("[PROTECTED]");
    }

    @Test
    void usernameDuplicadoDa409YNoCreaNada() {
        when(userRepo.existsByUsername("nuevodueno")).thenReturn(true);

        assertThrowsStatus(HttpStatus.CONFLICT, () -> service.createOwner(ADMIN_ID,
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "Libreria Sur")));

        verify(userRepo, never()).saveAndFlush(any());
        verifyNoInteractions(libraryRepo, passwordGenerator);
    }

    @Test
    void emailDuplicadoDa409YNoCreaNada() {
        when(userRepo.existsByEmail("dueno@test.local")).thenReturn(true);

        assertThrowsStatus(HttpStatus.CONFLICT, () -> service.createOwner(ADMIN_ID,
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "Libreria Sur")));

        verifyNoInteractions(libraryRepo, passwordGenerator);
    }

    @Test
    void duplicadoConcurrenteDa409YBorraLaPasswordTemporalDeMemoria() {
        char[] temporal = TEMPORAL.toCharArray();
        when(passwordGenerator.generate()).thenReturn(temporal);
        when(userRepo.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrowsStatus(HttpStatus.CONFLICT, () -> service.createOwner(ADMIN_ID,
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "Libreria Sur")));

        assertThat(temporal).containsOnly('\0');
        verifyNoInteractions(libraryRepo);
    }

    @Test
    void siFallaElAltaDeLaLibreriaSePropagaElErrorYSeBorraLaPasswordTemporal() {
        // La transaccion revierte el usuario; aca se verifica que el error no se trague
        char[] temporal = TEMPORAL.toCharArray();
        when(passwordGenerator.generate()).thenReturn(temporal);
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        when(libraryRepo.saveAndFlush(any(Library.class))).thenThrow(new IllegalStateException("db caida"));

        assertThatThrownBy(() -> service.createOwner(ADMIN_ID,
                        new CreateOwnerRequest("nuevodueno", "dueno@test.local", "Libreria Sur")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(temporal).containsOnly('\0');
    }

    static Stream<CreateOwnerRequest> requestsInvalidos() {
        return Stream.of(
                null,
                new CreateOwnerRequest(null, "dueno@test.local", "Libreria Sur"),
                new CreateOwnerRequest("ab", "dueno@test.local", "Libreria Sur"),
                new CreateOwnerRequest("a".repeat(31), "dueno@test.local", "Libreria Sur"),
                new CreateOwnerRequest("con espacio", "dueno@test.local", "Libreria Sur"),
                new CreateOwnerRequest("dueno;drop", "dueno@test.local", "Libreria Sur"),
                new CreateOwnerRequest("nuevodueno", null, "Libreria Sur"),
                new CreateOwnerRequest("nuevodueno", "no-es-mail", "Libreria Sur"),
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", null),
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "   "),
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "x"),
                new CreateOwnerRequest("nuevodueno", "dueno@test.local", "x".repeat(151)));
    }

    @ParameterizedTest
    @MethodSource("requestsInvalidos")
    void requestInvalidoDa400SinTocarLaBase(CreateOwnerRequest request) {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.createOwner(ADMIN_ID, request));

        verifyNoInteractions(userRepo, libraryRepo, passwordGenerator);
    }
}
