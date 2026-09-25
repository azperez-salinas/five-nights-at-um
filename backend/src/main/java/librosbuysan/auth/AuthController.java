package librosbuysan.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.auth.AuthDtos.AuthResponse;
import librosbuysan.auth.AuthDtos.LoginRequest;
import librosbuysan.auth.AuthDtos.RegisterDuenoRequest;
import librosbuysan.auth.AuthDtos.RegisterRequest;

/**
 * Sin @Valid a proposito: la validacion la hace AuthService dentro de su
 * try/finally, para que la contrasena se borre aunque el request sea invalido.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return withNoStore(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/register-dueno")
    public ResponseEntity<AuthResponse> registerDueno(@RequestBody RegisterDuenoRequest request) {
        return withNoStore(HttpStatus.CREATED, authService.registerDueno(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return withNoStore(HttpStatus.OK, authService.login(request));
    }

    // Las respuestas contienen el token: no deben quedar en caches
    private static ResponseEntity<AuthResponse> withNoStore(HttpStatus status, AuthResponse body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }
}