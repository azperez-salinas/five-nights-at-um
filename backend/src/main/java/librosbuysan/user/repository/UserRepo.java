package librosbuysan.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    // R18: AdminSeeder solo crea el admin si todavia no hay ninguno
    boolean existsByRole(User.Role role);
}
