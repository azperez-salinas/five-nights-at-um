package librosbuysan.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepo;

public interface UserRepo extends JpaRepo<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);
}
