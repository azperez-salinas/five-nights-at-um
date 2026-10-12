package librosbuysan.favorite;

import static librosbuysan.support.HttpAssertions.assertThrowsStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import librosbuysan.book.Book;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.book.BookRepo;
import librosbuysan.favorite.FavoriteService.AddResult;
import librosbuysan.library.Library;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * R15/F15 a nivel de service: toda operacion sobre favoritos queda acotada al
 * userId recibido (que el controller saca del token), nunca a un dato del request.
 */
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long BOOK_ID = 10L;

    @Mock private FavoriteRepo favoriteRepo;
    @Mock private BookRepo bookRepo;
    @Mock private UserRepo userRepo;

    private FavoriteService service;
    private Book libro;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(favoriteRepo, bookRepo, userRepo);
        Library library = new Library(User.dueno("dueno", "d@test.local", "hash"), "Libreria Central");
        libro = new Book(library, "9789900000013", "Hamlet", "Shakespeare",
                LocalDate.of(1601, 1, 1), 342, "Un principe", null);
    }

    @Test
    void agregarCreaElFavoritoAsociadoAlUsuarioRecibido() {
        User usuarioA = new User("a", "a@test.local", "hash");
        when(favoriteRepo.existsByUserIdAndBookId(USER_A, BOOK_ID)).thenReturn(false);
        when(bookRepo.findByIdAndLibraryEnabledTrue(BOOK_ID)).thenReturn(Optional.of(libro));
        when(userRepo.getReferenceById(USER_A)).thenReturn(usuarioA);

        AddResult result = service.add(USER_A, BOOK_ID);

        assertThat(result).isEqualTo(AddResult.CREATED);
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepo).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(usuarioA);
        assertThat(captor.getValue().getBook()).isSameAs(libro);
    }

    @Test
    void agregarUnFavoritoQueYaTieneElUsuarioEsIdempotenteYNoDuplica() {
        when(favoriteRepo.existsByUserIdAndBookId(USER_A, BOOK_ID)).thenReturn(true);

        assertThat(service.add(USER_A, BOOK_ID)).isEqualTo(AddResult.ALREADY_EXISTED);

        verify(favoriteRepo, never()).save(any());
    }

    @Test
    void elFavoritoDeOtroUsuarioNoCuentaComoYaExistente() {
        // B ya tiene el libro; A todavia no: a A se le crea el suyo
        when(favoriteRepo.existsByUserIdAndBookId(USER_A, BOOK_ID)).thenReturn(false);
        when(bookRepo.findByIdAndLibraryEnabledTrue(BOOK_ID)).thenReturn(Optional.of(libro));
        when(userRepo.getReferenceById(USER_A)).thenReturn(new User("a", "a@test.local", "hash"));

        assertThat(service.add(USER_A, BOOK_ID)).isEqualTo(AddResult.CREATED);

        verify(favoriteRepo, never()).existsByUserIdAndBookId(USER_B, BOOK_ID);
    }

    @Test
    void agregarUnLibroInexistenteODeLibreriaDeshabilitadaEs404() {
        when(favoriteRepo.existsByUserIdAndBookId(USER_A, BOOK_ID)).thenReturn(false);
        when(bookRepo.findByIdAndLibraryEnabledTrue(BOOK_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.add(USER_A, BOOK_ID));

        verify(favoriteRepo, never()).save(any());
    }

    @Test
    void quitarBuscaElFavoritoAcotadoAlUsuarioYLoElimina() {
        Favorite favorito = new Favorite(new User("a", "a@test.local", "hash"), libro);
        when(favoriteRepo.findByUserIdAndBookId(USER_A, BOOK_ID)).thenReturn(Optional.of(favorito));

        service.remove(USER_A, BOOK_ID);

        verify(favoriteRepo).delete(favorito);
    }

    @Test
    void quitarElFavoritoDeOtroUsuarioEs404YNoBorraNada() {
        // La busqueda es por (userId, bookId): el favorito de A no aparece para B
        when(favoriteRepo.findByUserIdAndBookId(USER_B, BOOK_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.remove(USER_B, BOOK_ID));

        verify(favoriteRepo, never()).delete(any());
    }

    @Test
    void listarSoloConsultaLosFavoritosDelUsuarioRecibido() {
        User usuarioA = new User("a", "a@test.local", "hash");
        when(favoriteRepo.findVisibleByUserId(USER_A)).thenReturn(List.of(new Favorite(usuarioA, libro)));

        List<BookSummary> result = service.list(USER_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).titulo()).isEqualTo("Hamlet");
        verify(favoriteRepo).findVisibleByUserId(USER_A);
        verify(favoriteRepo, never()).findAll();
        verify(favoriteRepo, never()).findVisibleByUserId(USER_B);
    }

    @Test
    void listarDevuelveVacioSiElUsuarioNoTieneFavoritos() {
        when(favoriteRepo.findVisibleByUserId(USER_B)).thenReturn(List.of());

        assertThat(service.list(USER_B)).isEmpty();
    }
}