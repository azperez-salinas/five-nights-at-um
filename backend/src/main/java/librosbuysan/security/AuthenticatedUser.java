package librosbuysan.security;

import librosbuysan.user.User;

public record AuthenticatedUser(Long id, User.Role role) {
}