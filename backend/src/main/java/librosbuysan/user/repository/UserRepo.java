package librosbuysan.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import librosbuysan.user.entity.User;

public interface UserRepo extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);
}
