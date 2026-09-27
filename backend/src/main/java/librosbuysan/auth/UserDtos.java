package librosbuysan.user;

import jakarta.validation.constraints.Pattern;
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

    public record UpdateProfileRequest(
            @Size(min = 3, max = 30)
            @Pattern(regexp = "^[A-Za-z0-9_]+$")
            String username,

            @Size(max = 64)
            char[] currentPassword,

            @Size(min = 8, max = 64)
            char[] newPassword) {

        @Override
        public String toString() {
            return "UpdateProfileRequest[username=" + username
                    + ", currentPassword=[PROTECTED], newPassword=[PROTECTED]]";
        }
    }
}