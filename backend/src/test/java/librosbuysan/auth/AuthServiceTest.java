package librosbuysan.auth;

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
import java.util.Optional;
import java.util.stream.Stream;
import librosbuysan.auth.AuthDtos.AuthResponse;
import librosbuysan.auth.AuthDtos.LoginRequest;
import librosbuysan.auth.AuthDtos.RegisterDuenoRequest;
import librosbuysan.auth.AuthDtos.RegisterRequest;
import librosbuysan.library.Library;
import librosbuysan.library.LibraryRepo;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "ClaveSegura123";
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    // bcrypt costo 12 es lento: se calcula una sola vez para toda la clase
    private static String passwordHash;

    @Mock private UserRepo userRepo;
    @Mock private LibraryRepo libraryRepo;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeAll
    static void calcularHash() {
        passwordHash = new PasswordHasher().hash(PASSWORD.toCharArray());
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, libraryRepo, jwtService, VALIDATOR);
    }

    private void jwtDevuelveToken(User.Role role, long segundos) {
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-de-prueba");
        when(jwtService.getExpirationSeconds(role)).thenReturn(segundos);
    }

    private User usuarioExistente(User.Role role) {
        User user = role == User.Role.DUENO
                ? User.dueno("pepe", "pepe@test.local", passwordHash)
                : new User("pepe", "pepe@test.local", passwordHash);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    // ================= register =================

    @Test
    void registerCreaUnCompradorNormalizaDatosHasheaLaPasswordYDevuelveToken() {
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        jwtDevuelveToken(User.Role.COMPRADOR, 3600);
        char[] password = PASSWORD.toCharArray();
        char[] copia = password.clone();

        AuthResponse response = authService.register(new RegisterRequest("NuevoUser", "Nuevo@Test.Local", password));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).saveAndFlush(captor.capture());
        User guardado = captor.getValue();
        assertThat(guardado.getUsername()).isEqualTo("nuevouser");
        assertThat(guardado.getEmail()).isEqualTo("nuevo@test.local");
        assertThat(guardado.getRole()).isEqualTo(User.Role.COMPRADOR);
        assertThat(guardado.isEnabled()).isTrue();
        assertThat(guardado.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(new PasswordHasher().matches(copia, guardado.getPasswordHash())).isTrue();

        assertThat(response.token()).isEqualTo("jwt-de-prueba");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.username()).isEqualTo("nuevouser");
        assertThat(response.role()).isEqualTo(User.Role.COMPRADOR);
    }

    @Test
    void registerBorraLaPasswordDeMemoria() {
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        jwtDevuelveToken(User.Role.COMPRADOR, 3600);
        char[] password = PASSWORD.toCharArray();

        authService.register(new RegisterRequest("nuevouser", "nuevo@test.local", password));

        assertThat(password).containsOnly('\0');
    }

    @Test
    void registerDa409SiElUsernameYaExiste() {
        when(userRepo.existsByUsername("nuevouser")).thenReturn(true);

        assertThrowsStatus(HttpStatus.CONFLICT, () -> authService
                .register(new RegisterRequest("nuevouser", "nuevo@test.local", PASSWORD.toCharArray())));

        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void registerDa409SiElEmailYaExiste() {
        when(userRepo.existsByEmail("nuevo@test.local")).thenReturn(true);

        assertThrowsStatus(HttpStatus.CONFLICT, () -> authService
                .register(new RegisterRequest("nuevouser", "nuevo@test.local", PASSWORD.toCharArray())));

        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void registerDa409SiOtroRegistroGanoLaCarreraEnLaBase() {
        when(userRepo.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThrowsStatus(HttpStatus.CONFLICT, () -> authService
                .register(new RegisterRequest("nuevouser", "nuevo@test.local", PASSWORD.toCharArray())));
    }

    @Test
    void registerRechazaRequestNulo() {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.register(null));
        verifyNoInteractions(userRepo);
    }

    static Stream<RegisterRequest> registrosInvalidos() {
        String largo65 = "a".repeat(65);
        return Stream.of(
                new RegisterRequest("ab", "ok@test.local", "ClaveSegura123".toCharArray()),
                new RegisterRequest("a".repeat(31), "ok@test.local", "ClaveSegura123".toCharArray()),
                new RegisterRequest("nombre con espacios", "ok@test.local", "ClaveSegura123".toCharArray()),
                new RegisterRequest("nombre;drop", "ok@test.local", "ClaveSegura123".toCharArray()),
                new RegisterRequest(null, "ok@test.local", "ClaveSegura123".toCharArray()),
                new RegisterRequest("validuser", "no-es-un-mail", "ClaveSegura123".toCharArray()),
                new RegisterRequest("validuser", null, "ClaveSegura123".toCharArray()),
                new RegisterRequest("validuser", "ok@test.local", "corta".toCharArray()),
                new RegisterRequest("validuser", "ok@test.local", largo65.toCharArray()),
                new RegisterRequest("validuser", "ok@test.local", null),
                // 40 caracteres pasan @Size pero son 80 bytes UTF-8 (> 72 de bcrypt)
                new RegisterRequest("validuser", "ok@test.local", "\u00f1".repeat(40).toCharArray()));
    }

    @ParameterizedTest
    @MethodSource("registrosInvalidos")
    void registerRechazaDatosInvalidosSinTocarLaBase(RegisterRequest request) {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.register(request));
        verifyNoInteractions(userRepo, libraryRepo, jwtService);
    }

    // ================= registerDueno =================

    @Test
    void registerDuenoCreaUnUsuarioDuenoYSuLibreriaHabilitada() {
        when(userRepo.saveAndFlush(any(User.class))).then(returnsFirstArg());
        jwtDevuelveToken(User.Role.DUENO, 900);

        AuthResponse response = authService.registerDueno(new RegisterDuenoRequest(
                "duenonuevo", "dueno@test.local", PASSWORD.toCharArray(), "  Mi Libreria  "));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.Role.DUENO);

        ArgumentCaptor<Library> libraryCaptor = ArgumentCaptor.forClass(Library.class);
        verify(libraryRepo).saveAndFlush(libraryCaptor.capture());
        assertThat(libraryCaptor.getValue().getNombre()).isEqualTo("Mi Libreria");
        assertThat(libraryCaptor.getValue().getDueno()).isSameAs(userCaptor.getValue());
        assertThat(libraryCaptor.getValue().isEnabled()).isTrue();

        assertThat(response.role()).isEqualTo(User.Role.DUENO);
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void registerDuenoDa409SiElUsuarioYaExisteYNoCreaLibreria() {
        when(userRepo.existsByUsername("duenonuevo")).thenReturn(true);

        assertThrowsStatus(HttpStatus.CONFLICT, () -> authService.registerDueno(new RegisterDuenoRequest(
                "duenonuevo", "dueno@test.local", PASSWORD.toCharArray(), "Mi Libreria")));

        verifyNoInteractions(libraryRepo);
    }

    @Test
    void registerDuenoRechazaNombreDeLibreriaEnBlancoOMuyCorto() {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.registerDueno(new RegisterDuenoRequest(
                "duenonuevo", "dueno@test.local", PASSWORD.toCharArray(), "   ")));
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.registerDueno(new RegisterDuenoRequest(
                "duenonuevo", "dueno@test.local", PASSWORD.toCharArray(), "x")));
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.registerDueno(null));

        verifyNoInteractions(userRepo, libraryRepo);
    }

    // ================= login =================

    @Test
    void loginCorrectoDevuelveToken() {
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(usuarioExistente(User.Role.COMPRADOR)));
        jwtDevuelveToken(User.Role.COMPRADOR, 3600);

        AuthResponse response = authService.login(new LoginRequest("pepe", PASSWORD.toCharArray()));

        assertThat(response.token()).isEqualTo("jwt-de-prueba");
        assertThat(response.username()).isEqualTo("pepe");
        assertThat(response.role()).isEqualTo(User.Role.COMPRADOR);
    }

    @Test
    void loginNormalizaElUsername() {
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(usuarioExistente(User.Role.COMPRADOR)));
        jwtDevuelveToken(User.Role.COMPRADOR, 3600);

        assertThat(authService.login(new LoginRequest("PEPE", PASSWORD.toCharArray())).username()).isEqualTo("pepe");
    }

    @Test
    void loginBorraLaPasswordDeMemoria() {
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(usuarioExistente(User.Role.COMPRADOR)));
        jwtDevuelveToken(User.Role.COMPRADOR, 3600);
        char[] password = PASSWORD.toCharArray();

        authService.login(new LoginRequest("pepe", password));

        assertThat(password).containsOnly('\0');
    }

    @Test
    void loginConPasswordIncorrectaDa401() {
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(usuarioExistente(User.Role.COMPRADOR)));

        assertThrowsStatus(HttpStatus.UNAUTHORIZED,
                () -> authService.login(new LoginRequest("pepe", "incorrecta999".toCharArray())));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginConUsuarioInexistenteDa401() {
        when(userRepo.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.UNAUTHORIZED,
                () -> authService.login(new LoginRequest("fantasma", PASSWORD.toCharArray())));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginConUsuarioDeshabilitadoDa401AunqueLaPasswordSeaCorrecta() {
        User deshabilitado = usuarioExistente(User.Role.COMPRADOR);
        ReflectionTestUtils.setField(deshabilitado, "enabled", false);
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(deshabilitado));

        assertThrowsStatus(HttpStatus.UNAUTHORIZED,
                () -> authService.login(new LoginRequest("pepe", PASSWORD.toCharArray())));

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginUsaElMismoMensajeParaLosTresMotivosDeFallo() {
        // RS31: no revelar si el usuario existe, esta deshabilitado o la password es incorrecta
        User deshabilitado = usuarioExistente(User.Role.COMPRADOR);
        ReflectionTestUtils.setField(deshabilitado, "enabled", false);
        when(userRepo.findByUsername("deshabilitado")).thenReturn(Optional.of(deshabilitado));
        when(userRepo.findByUsername("pepe")).thenReturn(Optional.of(usuarioExistente(User.Role.COMPRADOR)));
        when(userRepo.findByUsername("fantasma")).thenReturn(Optional.empty());

        String m1 = razonDeFallo("deshabilitado", PASSWORD);
        String m2 = razonDeFallo("pepe", "incorrecta999");
        String m3 = razonDeFallo("fantasma", PASSWORD);

        assertThat(m1).isEqualTo(m2).isEqualTo(m3).isNotBlank();
    }

    private String razonDeFallo(String username, String password) {
        try {
            authService.login(new LoginRequest(username, password.toCharArray()));
        } catch (ResponseStatusException e) {
            return e.getReason();
        }
        throw new AssertionError("Se esperaba un fallo de login");
    }

    @Test
    void loginRechazaRequestsInvalidosSinTocarLaBase() {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.login(null));
        assertThrowsStatus(HttpStatus.BAD_REQUEST,
                () -> authService.login(new LoginRequest("", PASSWORD.toCharArray())));
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> authService.login(new LoginRequest("pepe", null)));
        assertThrowsStatus(HttpStatus.BAD_REQUEST,
                () -> authService.login(new LoginRequest("pepe", "a".repeat(65).toCharArray())));
        assertThrowsStatus(HttpStatus.BAD_REQUEST,
                () -> authService.login(new LoginRequest("a".repeat(31), PASSWORD.toCharArray())));

        verifyNoInteractions(userRepo);
    }
}
