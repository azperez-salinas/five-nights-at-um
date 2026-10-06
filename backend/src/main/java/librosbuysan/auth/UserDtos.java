package librosbuysan.user;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record ProfileResponse(
            Long id,
            String username,
            String email,
            User.Role role,
            Instant createdAt) {

        public static ProfileResponse from(User user) {
            return new ProfileResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getRole(), user.getCreatedAt());
        }
    }

    /**
     * RS7/RS36: el unico campo modificable del perfil es la contrasena. El
     * username no es editable, asi que no forma parte del DTO: si el JSON lo
     * trae (o trae role, email, id, etc.) se ignora.
     */
    public record UpdateProfileRequest(
            @Size(max = 64)
            char[] currentPassword,

            @Size(min = 8, max = 64)
            char[] newPassword) {

        @Override
        public String toString() {
            return "UpdateProfileRequest[currentPassword=[PROTECTED], newPassword=[PROTECTED]]";
        }
    }
}