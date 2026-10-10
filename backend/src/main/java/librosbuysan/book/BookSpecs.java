package librosbuysan.book;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import librosbuysan.library.Library;

/**
 * R10/F10: arma el WHERE del catalogo dinamicamente, agregando solo las
 * condiciones de los filtros presentes. Los valores del usuario viajan como
 * parametros de la Criteria API (nunca concatenados en la consulta), asi que
 * no hay inyeccion SQL/JPQL.
 */
final class BookSpecs {

    private static final char LIKE_ESCAPE = '\\';

    private BookSpecs() {
    }

    /**
     * @param libreriaId filtro por libreria, o null para no filtrar
     * @param autor      texto contenido en el autor (sin distinguir mayusculas), o null
     */
    static Specification<Book> catalogFilter(Long libreriaId, String autor) {
        return (root, query, cb) -> {
            Join<Book, Library> library = root.join("library");
            List<Predicate> conditions = new ArrayList<>();

            // RS34: siempre, haya o no filtros
            conditions.add(cb.isTrue(library.get("enabled")));

            if (libreriaId != null) {
                conditions.add(cb.equal(library.get("id"), libreriaId));
            }
            if (autor != null) {
                String pattern = "%" + escapeLike(autor.toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.like(cb.lower(root.get("autor")), pattern, LIKE_ESCAPE));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    // Un "%" o "_" escrito por el usuario se busca literal, no como comodin
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
