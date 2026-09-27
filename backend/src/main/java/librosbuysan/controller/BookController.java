package librosbuysan.book;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.book.BookDtos.CatalogPage;

/**
 * Endpoint de solo lectura: catalogo publico de libros (R7), sin filtrar
 * por libreria (Book todavia no tiene relacion con Library). Las
 * funcionalidades de negocio especificas de cada libreria (busqueda, alta
 * por el dueno, stock, etc.) se suman despues sobre este mismo paquete.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public CatalogPage list(@RequestParam(defaultValue = "1") int page) {
        return bookService.getCatalog(page);
    }
}