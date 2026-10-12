package librosbuysan.book;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.book.BookDtos.CatalogPage;
import librosbuysan.security.AuthenticatedUser;

/**
 * Endpoints del catálogo de libros: listado paginado (R7),
 * búsqueda por texto libre (RF9), detalle por ID e importación
 * masiva mediante CSV.
 *
 * Las operaciones de lectura respetan RS34: los libros de
 * librerías deshabilitadas no deben ser visibles.
 *
 * La autorización de cada endpoint debe estar definida en
 * SecurityConfig y aplicar las reglas correspondientes a cada
 * método HTTP.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;
    private final BookCsvImportService csvImportService;

    public BookController(
            BookService bookService,
            BookCsvImportService csvImportService) {
        this.bookService = bookService;
        this.csvImportService = csvImportService;
    }

    // R10: librería y autor son filtros opcionales y combinables.
    @GetMapping
    public CatalogPage list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Long libreria,
            @RequestParam(required = false) String autor) {
        return bookService.getCatalog(page, libreria, autor);
    }

    // RF9: búsqueda por título, autor o ISBN.
    @GetMapping("/search")
    public CatalogPage search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page) {
        return bookService.search(q, page);
    }

    // RS34: detalle público de un libro.
    @GetMapping("/{id}")
    public BookSummary detail(@PathVariable Long id) {
        return bookService.getDetail(id);
    }

    // Importación masiva de libros mediante CSV.
    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importCsv(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        AuthenticatedUser user =
                (AuthenticatedUser) authentication.getPrincipal();

        return csvImportService.importCsv(user.id(), file);
    }
}
