package librosbuysan.favorite;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.book.Book;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.book.BookRepo;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;

@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    // RS31/RS38: mensajes genericos, no distinguen "no existe" de "es de
    // otro usuario" ni de ningun detalle interno de la consulta.
    private static final String BOOK_NOT_FOUND = "Libro no encontrado";
    private static final String FAVORITE_NOT_FOUND = "Favorito no encontrado";

    /**
     * Resultado de intentar agregar un favorito, para que el controller
     * decida el status HTTP (201 si se creo, 200 si ya existia).
     */
    public enum AddResult {
        CREATED,
        ALREADY_EXISTED
    }

    private final FavoriteRepo favoriteRepo;
    private final BookRepo bookRepo;
    private final UserRepo userRepo;

    public FavoriteService(FavoriteRepo favoriteRepo, BookRepo bookRepo, UserRepo userRepo) {
        this.favoriteRepo = favoriteRepo;
        this.bookRepo = bookRepo;
        this.userRepo = userRepo;
    }

    /**
     * RF12: agrega un libro a favoritos. userId viene siempre del token
     * (AuthenticatedUser en el controller), nunca de un campo del body
     * (RS36) — de hecho este endpoint no acepta body, solo el id del libro
     * por path y el usuario autenticado, asi que no hay ningun campo extra
     * que un cliente pueda intentar colar.
     */
    @Transactional
    public AddResult add(Long userId, Long bookId) {
        // Idempotencia (POST repetido no es error ni duplica la fila).
        if (favoriteRepo.existsByUserIdAndBookId(userId, bookId)) {
            return AddResult.ALREADY_EXISTED;
        }

        // RS34: no se puede favoritear un libro de una libreria deshabilitada;
        // se trata igual que si el libro no existiera (mismo criterio que el
        // detalle de libro en BookService.getDetail).
        Book book = bookRepo.findByIdAndLibraryEnabledTrue(bookId)
                .orElseThrow(() -> {
                    log.warn("Alta de favorito rechazada: userId={} bookId={} motivo=libro_no_encontrado",
                            userId, bookId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, BOOK_NOT_FOUND);
                });

        // getReferenceById: no hace falta traer el User completo, ya esta
        // autenticado (el JWT ya lo valido); solo se usa para la FK.
        User user = userRepo.getReferenceById(userId);

        try {
            favoriteRepo.save(new Favorite(user, book));
            log.info("Favorito agregado: userId={} bookId={}", userId, bookId);
            return AddResult.CREATED;
        } catch (DataIntegrityViolationException e) {
            // Carrera entre dos POST concurrentes del mismo usuario/libro:
            // el segundo choca contra la unique constraint. Se trata igual
            // que "ya existia", no como un error.
            log.info("Alta de favorito concurrente ya resuelta: userId={} bookId={}", userId, bookId);
            return AddResult.ALREADY_EXISTED;
        }
    }

    /**
     * RF12/RS24: quita un favorito. La busqueda ya esta acotada por userId,
     * asi que un favorito de otro usuario da el mismo 404 que uno
     * inexistente (no se revela si el recurso existe pero es ajeno).
     */
    @Transactional
    public void remove(Long userId, Long bookId) {
        Favorite favorite = favoriteRepo.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> {
                    log.warn("Baja de favorito rechazada: userId={} bookId={} motivo=no_encontrado_o_ajeno",
                            userId, bookId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, FAVORITE_NOT_FOUND);
                });
        favoriteRepo.delete(favorite);
        log.info("Favorito eliminado: userId={} bookId={}", userId, bookId);
    }

    /**
     * RF12: lista los favoritos del usuario autenticado. RS34: se excluyen
     * los libros cuya libreria fue deshabilitada despues de haberlos
     * agregado.
     */
    public List<BookSummary> list(Long userId) {
        return favoriteRepo.findVisibleByUserId(userId).stream()
                .map(favorite -> BookSummary.from(favorite.getBook()))
                .toList();
    }
}
