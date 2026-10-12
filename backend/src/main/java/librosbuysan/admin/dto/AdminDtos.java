package librosbuysan.admin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTOs del panel de administrador (R18).
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * Alta de un dueno. Funciona como allowlist (RS25): el rol no es un campo
     * del DTO, asi que si el JSON trae "role", "enabled", "password", etc. se
     * ignora. El rol lo fija siempre el backend (DUENO).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateOwnerRequest(
            @NotBlank
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$")
            String username,

            @NotBlank
            @Email
            @Size(max = 254)
            String email,

            @NotBlank
            @Size(min = 2, max = 150)
            String nombreLibreria) {
    }

    /**
     * La contrasena temporal viaja SOLO en esta respuesta: se guarda hasheada
     * y no hay ningun endpoint que la vuelva a mostrar. Se usa char[] (Jackson
     * lo serializa como string) para no crear un String con ella.
     */
    public record OwnerCreatedResponse(
            Long id,
            String username,
            String email,
            Long libreriaId,
            String nombreLibreria,
            char[] passwordTemporal) {

        @Override
        public String toString() {
            return "OwnerCreatedResponse[id=" + id + ", username=" + username + ", email=" + email
                    + ", libreriaId=" + libreriaId + ", nombreLibreria=" + nombreLibreria
                    + ", passwordTemporal=[PROTECTED]]";
        }
    }
}
