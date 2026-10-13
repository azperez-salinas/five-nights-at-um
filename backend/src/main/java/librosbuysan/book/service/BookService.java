package librosbuysan.book.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.book.dto.BookDtos.BookSummary;
import librosbuysan.book.dto.BookDtos.CatalogPage;
import librosbuysan.book.dto.BookDtos.UpdateBookRequest;
import librosbuysan.book.entity.Book;
import librosbuysan.book.repository.BookRepo;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;

@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    // Tamano de pagina fijo: el cliente no lo elige, asi la home siempre
    // muestra la misma cantidad de libros y no hay que validar un input mas.
    // Un solo lugar para cambiarlo si el diseño final pide otro numero.
    private static final int PAGE_SIZE = 12;

    private static final String INVALID_PAGE = "El parametro 'page' debe ser mayor o igual a 1";

    // RS31/RS38: mensaje generico de error de busqueda. No describe la causa
    // interna (por ejemplo, un fallo de query) para no filtrar detalles de
    // implementacion al cliente.
    private static final String INVALID_QUERY = "El parametro 'q' es obligatorio y no puede estar vacio";

    private static final String INVALID_FILTER = "Filtro invalido";

    // R18/F18/RS20: mismo mensaje para "la libreria del dueno no existe o
    // esta deshabilitada" — no se distingue el motivo.
    private static final String LIBRARY_NOT_FOUND = "Libreria no encontrada";

    // R18/F18/RS31/RS38: mismo mensaje para "no existe" y "es de otra
    // libreria" — no se puede enumerar libros ajenos (R6/IDOR).
    private static final String BOOK_NOT_FOUND = "Libro no encontrado";

    private static final String INVALID_BOOK_DATA = "Datos de libro invalidos";

    // Mismo largo maximo que la columna autor
    private static final int MAX_AUTOR_CHARS = 150;
    private static final int MAX_TITULO_CHARS = 255;
    private static final int MAX_ISBN_CHARS = 20;
    private static final int MAX_PORTADA_CHARS = 500;

    private final BookRepo bookRepo;
    private final LibraryRepo libraryRepo;

    public BookService(BookRepo bookRepo, LibraryRepo libraryRepo) {
        this.bookRepo = bookRepo;
        this.libraryRepo = libraryRepo;
    }

    /**
     * R10/F10: catalogo paginado con filtros opcionales y combinables. Sin
     * filtros devuelve el catalogo completo (R7).
     */
    public CatalogPage getCatalog(int page, Long libreriaId, String autor) {
        validatePage(page);
        String autorFilter = validateAutor(autor);
        if (libreriaId != null && libreriaId < 1) {
            log.warn("Solicitud de catalogo invalida: libreria={}", libreriaId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_FILTER);
        }

        // Orden explicito: sin ORDER BY, Postgres no garantiza el mismo orden
        // entre dos consultas paginadas separadas (podria repetir o saltear
        // libros al pasar de una pagina a otra).
        Sort orderById = Sort.by("id").ascending();
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE, orderById);

        // RS34: solo libros de librerias habilitadas (BookSpecs lo agrega siempre).
        Page<Book> result = bookRepo.findAll(BookSpecs.catalogFilter(libreriaId, autorFilter), pageable);
        List<BookSummary> items = result.getContent().stream().map(BookSummary::from).toList();

        return new CatalogPage(items, page, PAGE_SIZE, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * RF9: busqueda por texto libre en titulo, autor o ISBN. La restriccion
     * de que solo un COMPRADOR autenticado pueda llamar a este metodo (RS1)
     * se resuelve antes de llegar aca, en SecurityConfig — este servicio no
     * vuelve a chequear el rol.
     */
    public CatalogPage search(String q, int page) {
        validatePage(page);
        String query = validateQuery(q);

        Sort orderById = Sort.by("id").ascending();
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE, orderById);

        // RS34: la busqueda tambien excluye libros de librerias deshabilitadas.
        Page<Book> result = bookRepo.search(query, pageable);
        List<BookSummary> items = result.getContent().stream().map(BookSummary::from).toList();

        return new CatalogPage(items, page, PAGE_SIZE, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * RF: detalle de un libro por id. RS34: si el libro no existe, o su
     * libreria esta deshabilitada, se responde 404 igual en ambos casos (no
     * se distingue el motivo), para no revelar que el libro existio alguna
     * vez bajo una libreria dada de baja.
     */
    public BookSummary getDetail(Long id) {
        Book book = bookRepo.findByIdAndEnabledTrueAndLibraryEnabledTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, BOOK_NOT_FOUND));
        return BookSummary.from(book);
    }

    /**
     * R18/F18: catalogo propio del dueno autenticado. La libreria se resuelve
     * SIEMPRE a partir del userId del token (RS6-equivalente para libros): el
     * endpoint no recibe libreriaId por parametro, asi que no hay forma de
     * pedir el catalogo de otra libreria desde aca.
     */
    public CatalogPage getMine(Long userId, int page) {
        validatePage(page);
        Library library = ownEnabledLibrary(userId);

        Sort orderById = Sort.by("id").ascending();
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE, orderById);

        Page<Book> result = bookRepo.findByLibrary_IdAndEnabledTrue(library.getId(), pageable);
        List<BookSummary> items = result.getContent().stream().map(BookSummary::from).toList();

        return new CatalogPage(items, page, PAGE_SIZE, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * R18/F18/RS20/RS24: edita un libro de la PROPIA libreria del dueno.
     * Actualizacion parcial (campo null = no se toca). RS36/RS2/RS7: el unico
     * origen de los campos editables es UpdateBookRequest, que ya es un
     * allowlist — no hay forma de que este metodo reciba libreriaId ni
     * isEnabled del cliente.
     */
    @Transactional
    public BookSummary updateBook(Long userId, Long bookId, UpdateBookRequest request) {
        Library library = ownEnabledLibrary(userId);
        Book book = ownBook(library, bookId);

        if (request.titulo() != null) {
            String titulo = validateField(request.titulo(), MAX_TITULO_CHARS, "titulo");
            book.changeTitulo(titulo);
        }
        if (request.autor() != null) {
            String autor = validateField(request.autor(), MAX_AUTOR_CHARS, "autor");
            book.changeAutor(autor);
        }
        if (request.isbn() != null) {
            String isbn = validateField(request.isbn(), MAX_ISBN_CHARS, "isbn");
            if (!IsbnValidator.isValid(isbn)) {
                log.warn("Edicion de libro rechazada: userId={} bookId={} motivo=isbn_invalido", userId, bookId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_BOOK_DATA);
            }
            book.changeIsbn(isbn);
        }
        if (request.portadaUrl() != null) {
            String portada = validateField(request.portadaUrl(), MAX_PORTADA_CHARS, "portadaUrl");
            book.changePortadaUrl(portada);
        }

        Book saved = bookRepo.save(book);
        log.info("Libro editado: userId={} bookId={}", userId, bookId);
        return BookSummary.from(saved);
    }

    /**
     * R18/F18/RS20: baja logica de un libro de la PROPIA libreria del dueno.
     */
    @Transactional
    public void deleteBook(Long userId, Long bookId) {
        Library library = ownEnabledLibrary(userId);
        Book book = ownBook(library, bookId);
        book.disable();
        bookRepo.save(book);
        log.info("Libro dado de baja: userId={} bookId={}", userId, bookId);
    }

    // R18/F18: libreria del dueno autenticado. Si no tiene libreria, o la
    // tiene deshabilitada, ninguna operacion de R18 esta disponible.
    private Library ownEnabledLibrary(Long userId) {
        return libraryRepo.findByDuenoIdAndEnabledTrue(userId)
                .orElseThrow(() -> {
                    log.warn("Operacion de libro rechazada: userId={} motivo=sin_libreria_habilitada", userId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, LIBRARY_NOT_FOUND);
                });
    }

    // R6(IDOR)/RS20/RS31/RS38: el mismo 404 para libro inexistente, de otra
    // libreria, o ya dado de baja — no se revela cual de los tres motivos es.
    private Book ownBook(Library library, Long bookId) {
        return bookRepo.findByIdAndLibrary_IdAndEnabledTrue(bookId, library.getId())
                .orElseThrow(() -> {
                    log.warn("Operacion de libro rechazada: libraryId={} bookId={} motivo=no_encontrado_o_ajeno",
                            library.getId(), bookId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, BOOK_NOT_FOUND);
                });
    }

    private String validateField(String value, int maxLength, String fieldName) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            log.warn("Edicion de libro rechazada: motivo=campo_invalido campo={}", fieldName);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_BOOK_DATA);
        }
        return trimmed;
    }

    private void validatePage(int page) {
        if (page < 1) {
            log.warn("Solicitud de catalogo invalida: page={}", page);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PAGE);
        }
    }

    // Autor vacio o ausente equivale a no filtrar por autor
    private String validateAutor(String autor) {
        if (autor == null || autor.isBlank()) {
            return null;
        }
        String trimmed = autor.trim();
        if (trimmed.length() > MAX_AUTOR_CHARS) {
            log.warn("Solicitud de catalogo invalida: autor demasiado largo");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_FILTER);
        }
        return trimmed;
    }

    private String validateQuery(String q) {
        if (q == null || q.isBlank()) {
            log.warn("Solicitud de busqueda invalida: q vacio o ausente");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_QUERY);
        }
        return q.trim();
    }
}