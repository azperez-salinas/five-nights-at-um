package librosbuysan.book;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.book.BookDtos.CatalogPage;

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

    private final BookRepo bookRepo;

    public BookService(BookRepo bookRepo) {
        this.bookRepo = bookRepo;
    }

    public CatalogPage getCatalog(int page) {
        validatePage(page);

        // Orden explicito: sin ORDER BY, Postgres no garantiza el mismo orden
        // entre dos consultas paginadas separadas (podria repetir o saltear
        // libros al pasar de una pagina a otra).
        Sort orderById = Sort.by("id").ascending();
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE, orderById);

        // RS34: solo libros de librerias habilitadas.
        Page<Book> result = bookRepo.findByLibraryEnabledTrue(pageable);
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
        Book book = bookRepo.findByIdAndLibraryEnabledTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro no encontrado"));
        return BookSummary.from(book);
    }

    private void validatePage(int page) {
        if (page < 1) {
            log.warn("Solicitud de catalogo invalida: page={}", page);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PAGE);
        }
    }

    private String validateQuery(String q) {
        if (q == null || q.isBlank()) {
            log.warn("Solicitud de busqueda invalida: q vacio o ausente");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_QUERY);
        }
        return q.trim();
    }
}