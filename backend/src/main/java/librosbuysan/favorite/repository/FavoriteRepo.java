package librosbuysan.favorite.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import librosbuysan.favorite.entity.Favorite;

public interface FavoriteRepo extends JpaRepository<Favorite, Long> {

    // Chequeo de idempotencia para el POST: si ya existe, no se crea de nuevo.
    boolean existsByUserIdAndBookId(Long userId, Long bookId);

    // RS24: la busqueda ya viene filtrada por userId, no solo por el id del
    // favorito. Un favorito de otro usuario simplemente no aparece en el
    // resultado (Optional vacio), lo que el service traduce a 404 sin tener
    // que comparar "dueno" a mano.
    Optional<Favorite> findByUserIdAndBookId(Long userId, Long bookId);

    // RS34: el listado de "mis favoritos" no muestra libros cuya libreria
    // fue deshabilitada, ni libros dados de baja individualmente (R18),
    // despues de haberlos agregado. JOIN FETCH trae libro y libreria en la
    // misma consulta (evita N+1 al mapear a BookSummary).
    @Query("""
            SELECT f FROM Favorite f
            JOIN FETCH f.book b
            JOIN FETCH b.library l
            WHERE f.user.id = :userId AND b.enabled = true AND l.enabled = true
            ORDER BY f.createdAt DESC
            """)
    List<Favorite> findVisibleByUserId(@Param("userId") Long userId);
}
