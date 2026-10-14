package librosbuysan.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import librosbuysan.user.entity.User;

public interface UserRepo extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    // R18: AdminSeeder solo crea el admin si todavia no hay ninguno
    boolean existsByRole(User.Role role);

    // R22: JwtAuthenticationFilter la consulta en cada request autenticado.
    // Devuelve false tanto si la cuenta esta deshabilitada como si ya no existe.
    boolean existsByIdAndEnabledTrue(Long id);
}
