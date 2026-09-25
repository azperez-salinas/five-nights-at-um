package librosbuysan.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import librosbuysan.user.User;

/**
 * DTOs de autenticacion. Funcionan como allowlist de campos (RS25): cualquier
 * otra propiedad del JSON (por ejemplo "role" o "enabled") se ignora.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$")
            String username,

            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotNull
            @Size(min = 8, max = 64)
            char[] password) {

        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + ", email=" + email + ", password=[PROTECTED]]";
        }
    }

    public record RegisterDuenoRequest(
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$")
            String username,

            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotNull
            @Size(min = 8, max = 64)
            char[] password,

            @NotBlank
            @Size(min = 2, max = 150)
            String nombreLibreria) {

        @Override
        public String toString() {
            return "RegisterDuenoRequest[username=" + username + ", email=" + email
                    + ", password=[PROTECTED], nombreLibreria=" + nombreLibreria + "]";
        }
    }

    public record LoginRequest(
            @NotBlank
            @Size(max = 30)
            String username,

            // El maximo evita procesar entradas enormes
            @NotNull
            @Size(max = 64)
            char[] password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=[PROTECTED]]";
        }
    }

    public record AuthResponse(
            String token,
            String tokenType,
            long expiresIn,
            String username,
            User.Role role) {

        public AuthResponse(String token, long expiresIn, String username, User.Role role) {
            this(token, "Bearer", expiresIn, username, role);
        }
    }
}
