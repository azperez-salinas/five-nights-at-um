package librosbuysan.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import librosbuysan.book.entity.Book;
import librosbuysan.library.entity.Library;
import librosbuysan.user.entity.User;
import org.hibernate.annotations.DynamicUpdate;
import org.junit.jupiter.api.Test;

/**
 * Contrato de minimo privilegio entre las entidades y db/init/02-grants.sh.
 *
 * La cuenta de BD de la app tiene UPDATE solo sobre columnas puntuales de
 * users, librarys y books. Eso solo funciona si Hibernate incluye en el UPDATE
 * unicamente las columnas modificadas (@DynamicUpdate); sin la anotacion
 * mandaria TODAS las columnas (incluida users.role) y Postgres rechazaria la
 * sentencia con "permission denied" recien en runtime.
 *
 * Este test falla en el build si alguien quita la anotacion. No reemplaza a
 * db/verify-privileges.sh, que comprueba los permisos contra la base real.
 */
class DbLeastPrivilegeContractTest {

    @Test
    void lasEntidadesConUpdateRestringidoUsanDynamicUpdate() {
        List<Class<?>> entidades = List.of(User.class, Library.class, Book.class);

        for (Class<?> entidad : entidades) {
            assertThat(entidad.isAnnotationPresent(DynamicUpdate.class))
                    .as("%s debe tener @DynamicUpdate (ver db/init/02-grants.sh)", entidad.getSimpleName())
                    .isTrue();
        }
    }
}