package librosbuysan.book;

import static librosbuysan.support.HttpAssertions.assertThrowsStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import librosbuysan.book.dto.BookDtos;
import librosbuysan.book.dto.BookDtos.BookSummary;
import librosbuysan.book.dto.BookDtos.UpdateBookRequest;
import librosbuysan.book.entity.Book;
import librosbuysan.book.repository.BookRepo;
import librosbuysan.book.service.BookService;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;
import librosbuysan.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * R18/F18: un dueno solo gestiona los libros de SU propia libreria
 * (RS20/RS24). Mismo patron que LibraryServiceTest: repos mockeados, sin
 * levantar el contexto de Spring.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final Long DUENO_ID = 1L;
    private static final Long OTRO_DUENO_ID = 2L;
    private static final Long LIBRARY_ID = 10L;
    private static final Long BOOK_ID = 100L;

    @Mock private BookRepo bookRepo;
    @Mock private LibraryRepo libraryRepo;

    private BookService service;
    private Library library;
    private Book book;

    @BeforeEach
    void setUp() {
        service = new BookService(bookRepo, libraryRepo);

        User dueno = User.dueno("dueno", "d@test.local", "hash");
        ReflectionTestUtils.setField(dueno, "id", DUENO_ID);
        library = new Library(dueno, "Libreria Central");
        ReflectionTestUtils.setField(library, "id", LIBRARY_ID);

        book = new Book(library, "9789900000013", "Don Quijote de la Mancha", "Miguel de Cervantes",
                LocalDate.of(1605, 1, 1), 863, "Descripcion", null);
        ReflectionTestUtils.setField(book, "id", BOOK_ID);
    }

    // ------------------------------------------------------------------
    // getMine
    // ------------------------------------------------------------------

    @Test
    void getMineBuscaPorElIdDeLaLibreriaDelDuenoAutenticado() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        Page<Book> page = new PageImpl<>(java.util.List.of(book));
        when(bookRepo.findByLibrary_IdAndEnabledTrue(eq(LIBRARY_ID), any(Pageable.class))).thenReturn(page);

        BookDtos.CatalogPage result = service.getMine(DUENO_ID, 1);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).titulo()).isEqualTo("Don Quijote de la Mancha");
        verify(bookRepo).findByLibrary_IdAndEnabledTrue(eq(LIBRARY_ID), any(Pageable.class));
    }

    @Test
    void getMineDeUnDuenoSinLibreriaHabilitadaEs404() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(OTRO_DUENO_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.getMine(OTRO_DUENO_ID, 1));
    }

    @Test
    void getMineConPageInvalidoEs400() {
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.getMine(DUENO_ID, 0));
        verify(libraryRepo, never()).findByDuenoIdAndEnabledTrue(any());
    }

    // ------------------------------------------------------------------
    // updateBook
    // ------------------------------------------------------------------

    @Test
    void elDuenoPuedeEditarUnLibroDeSuPropiaLibreria() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.of(book));
        when(bookRepo.save(book)).thenReturn(book);

        UpdateBookRequest request = new UpdateBookRequest("Nuevo titulo", null, null, null);
        BookSummary result = service.updateBook(DUENO_ID, BOOK_ID, request);

        assertThat(result.titulo()).isEqualTo("Nuevo titulo");
        // Campos no enviados no cambian (actualizacion parcial)
        assertThat(book.getAutor()).isEqualTo("Miguel de Cervantes");
    }

    @Test
    void camposNoEnviadosEnElPutNoSeModifican() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.of(book));
        when(bookRepo.save(book)).thenReturn(book);

        service.updateBook(DUENO_ID, BOOK_ID, new UpdateBookRequest(null, null, null, null));

        assertThat(book.getTitulo()).isEqualTo("Don Quijote de la Mancha");
        assertThat(book.getAutor()).isEqualTo("Miguel de Cervantes");
        assertThat(book.getIsbn()).isEqualTo("9789900000013");
        // libreriaId e isEnabled no son parte del DTO: no hay forma de tocarlos desde el PUT
        assertThat(book.getLibrary()).isSameAs(library);
        assertThat(book.isEnabled()).isTrue();
    }

    @Test
    void editarUnLibroDeOtraLibreriaEs404YNoLoModifica() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(OTRO_DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.empty());

        UpdateBookRequest request = new UpdateBookRequest("Otro titulo", null, null, null);
        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.updateBook(OTRO_DUENO_ID, BOOK_ID, request));

        assertThat(book.getTitulo()).isEqualTo("Don Quijote de la Mancha");
        verify(bookRepo, never()).save(any());
    }

    @Test
    void libroInexistenteYAjenoRespondenElMismoMensaje() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.empty());
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(999L, LIBRARY_ID)).thenReturn(Optional.empty());

        UpdateBookRequest request = new UpdateBookRequest("x", null, null, null);
        String ajeno = mensajeDe(() -> service.updateBook(DUENO_ID, BOOK_ID, request));
        String inexistente = mensajeDe(() -> service.updateBook(DUENO_ID, 999L, request));

        assertThat(ajeno).isEqualTo(inexistente);
    }

    @Test
    void editarConIsbnInvalidoEs400YNoGuardaNada() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.of(book));

        UpdateBookRequest request = new UpdateBookRequest(null, null, "no-es-un-isbn", null);
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.updateBook(DUENO_ID, BOOK_ID, request));

        assertThat(book.getIsbn()).isEqualTo("9789900000013");
        verify(bookRepo, never()).save(any());
    }

    @Test
    void editarConTituloVacioEs400() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.of(book));

        UpdateBookRequest request = new UpdateBookRequest("   ", null, null, null);
        assertThrowsStatus(HttpStatus.BAD_REQUEST, () -> service.updateBook(DUENO_ID, BOOK_ID, request));

        verify(bookRepo, never()).save(any());
    }

    @Test
    void editarSinLibreriaHabilitadaEs404() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.empty());

        UpdateBookRequest request = new UpdateBookRequest("x", null, null, null);
        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.updateBook(DUENO_ID, BOOK_ID, request));

        verify(bookRepo, never()).findByIdAndLibrary_IdAndEnabledTrue(any(), any());
    }

    // ------------------------------------------------------------------
    // deleteBook
    // ------------------------------------------------------------------

    @Test
    void elDuenoPuedeDarDeBajaUnLibroDeSuPropiaLibreria() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.of(book));

        service.deleteBook(DUENO_ID, BOOK_ID);

        assertThat(book.isEnabled()).isFalse();
        verify(bookRepo).save(book);
    }

    @Test
    void darDeBajaUnLibroAjenoEs404YNoLoModifica() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(OTRO_DUENO_ID)).thenReturn(Optional.of(library));
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.deleteBook(OTRO_DUENO_ID, BOOK_ID));

        assertThat(book.isEnabled()).isTrue();
        verify(bookRepo, never()).save(any());
    }

    @Test
    void darDeBajaUnLibroYaDadoDeBajaEs404() {
        book.disable();
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.of(library));
        // El libro ya deshabilitado no lo devuelve la query (findByIdAndLibrary_IdAndEnabledTrue)
        when(bookRepo.findByIdAndLibrary_IdAndEnabledTrue(BOOK_ID, LIBRARY_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.deleteBook(DUENO_ID, BOOK_ID));

        verify(bookRepo, never()).save(any());
    }

    @Test
    void darDeBajaSinLibreriaHabilitadaEs404() {
        when(libraryRepo.findByDuenoIdAndEnabledTrue(DUENO_ID)).thenReturn(Optional.empty());

        assertThrowsStatus(HttpStatus.NOT_FOUND, () -> service.deleteBook(DUENO_ID, BOOK_ID));

        verify(bookRepo, never()).findByIdAndLibrary_IdAndEnabledTrue(any(), any());
    }

    private static String mensajeDe(Runnable call) {
        try {
            call.run();
            throw new AssertionError("se esperaba ResponseStatusException");
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return e.getReason();
        }
    }
}
