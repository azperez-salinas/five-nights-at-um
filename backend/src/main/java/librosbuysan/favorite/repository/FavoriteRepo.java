package librosbuysan.favorite.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    // R13/F13/RS20: total de favoritos del panel de metricas, filtrado por la
    // libreria DEL LIBRO (book.library.id), nunca por un pedido/reserva
    // completo — asi un libro de otra libreria agregado a favoritos nunca
    // suma en el conteo de esta libreria (RS20). RS34: igual que
    // findVisibleByUserId, se excluyen libros o librerias dadas de baja.
    long countByBookLibraryIdAndBookEnabledTrueAndBookLibraryEnabledTrue(Long libraryId);

    // R13/F13: ranking "libros mas favoriteados" de UNA libreria puntual.
    // RS2/RS7: la proyeccion (BookFavoriteCount) solo trae datos del libro y
    // la cantidad, nunca el usuario que lo favoriteo. El limite de filas
    // (top N) lo controla el Pageable que pasa el caller (PageRequest.of(0, N)),
    // ordenado siempre por cantidad de favoritos descendente.
    @Query("""
            SELECT b.id AS bookId, b.titulo AS titulo, b.autor AS autor, COUNT(f) AS cantidadFavoritos
            FROM Favorite f
            JOIN f.book b
            WHERE b.library.id = :libraryId AND b.enabled = true AND b.library.enabled = true
            GROUP BY b.id, b.titulo, b.autor
            ORDER BY COUNT(f) DESC
            """)
    List<BookFavoriteCount> findTopFavoritedBooks(@Param("libraryId") Long libraryId, Pageable pageable);

    /** Proyeccion de solo lectura para el ranking de R13/F13 (ver findTopFavoritedBooks). */
    interface BookFavoriteCount {
        Long getBookId();
        String getTitulo();
        String getAutor();
        long getCantidadFavoritos();
    }
}
