package librosbuysan.book.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import librosbuysan.book.dto.BookDtos.BookSummary;
import librosbuysan.book.dto.BookDtos.CatalogPage;
import librosbuysan.book.dto.BookDtos.UpdateBookRequest;
import librosbuysan.book.dto.CsvImportResult;
import librosbuysan.book.service.BookCsvImportService;
import librosbuysan.book.service.BookService;
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
            @AuthenticationPrincipal AuthenticatedUser principal) {
        // RS11: mismo criterio que el resto de los endpoints, la libreria
        // destino sale del principal del token.
        return csvImportService.importCsv(principal.id(), file);
    }

    // R18/F18: catalogo propio del dueno autenticado. La libreria sale del
    // token (principal.id()), nunca de un parametro de la request — asi no
    // hay forma de pedir el catalogo de una libreria ajena desde aca.
    @GetMapping("/mine")
    public CatalogPage mine(@AuthenticationPrincipal AuthenticatedUser principal,
                            @RequestParam(defaultValue = "1") int page) {
        return bookService.getMine(principal.id(), page);
    }

    // R18/F18/RS36: edita un libro de la propia libreria del dueno. El body
    // es UpdateBookRequest (allowlist: titulo/autor/isbn/portadaUrl), nunca
    // acepta libreriaId ni isEnabled.
    @PutMapping("/{id}")
    public BookSummary update(@AuthenticationPrincipal AuthenticatedUser principal,
                              @PathVariable Long id,
                              @RequestBody UpdateBookRequest request) {
        return bookService.updateBook(principal.id(), id, request);
    }

    // R18/F18: baja logica de un libro de la propia libreria del dueno.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long id) {
        bookService.deleteBook(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
