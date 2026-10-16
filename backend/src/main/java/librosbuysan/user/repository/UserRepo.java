package librosbuysan.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import librosbuysan.user.entity.User;

public interface UserRepo extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    // R18: AdminSeeder solo crea el admin si todavia no hay ninguno
    boolean existsByRole(User.Role role);

    // R10/R22: JwtAuthenticationFilter la consulta en cada request autenticado.
    // Vacio tanto si la cuenta esta deshabilitada como si ya no existe; si esta
    // presente, es el rol VIGENTE para comparar contra el claim del token.
    @Query("select u.role from User u where u.id = :id and u.enabled = true")
    Optional<User.Role> findRoleByIdAndEnabledTrue(@Param("id") Long id);
}
