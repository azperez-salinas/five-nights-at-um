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

    // R10/R22/RS28: JwtAuthenticationFilter la consulta en cada request
    // autenticado. Devuelve false si la cuenta esta deshabilitada o no existe
    // (R22), si la version de las credenciales ya no es la vigente (RS28:
    // cambio de contrasena), o si el rol ya no es el vigente (R10: cambio de
    // permisos). Una sola consulta para los tres casos.
    boolean existsByIdAndEnabledTrueAndTokenVersionAndRole(Long id, int tokenVersion, User.Role role);
}
