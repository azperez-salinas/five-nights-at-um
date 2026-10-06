package librosbuysan.book;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.book.BookDtos.CatalogPage;

/**
 * Endpoints de solo lectura sobre el catalogo de libros: listado paginado
 * (R7), busqueda por texto libre (RF9) y detalle por id. Las tres
 * operaciones respetan RS34 (libros de librerias deshabilitadas no se ven).
 *
 * Todos los metodos son @GetMapping: si se llama a estas rutas con otro
 * verbo HTTP (POST, PUT, DELETE, etc.) la peticion se rechaza (RS33) sin
 * llegar al BookService. SecurityConfig solo permite GET sobre /api/books,
 * asi que el deny-by-default responde 401/403 antes de que Spring MVC
 * llegue a devolver 405 por falta de handler.
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

    // RF9: busqueda por texto libre (titulo, autor o ISBN). Publica, igual que
    // el catalogo: la regla vive centralizada en SecurityConfig (RS1/RS9), no
    // aca, para que toda la autorizacion de la app se pueda auditar en un
    // solo lugar.
    @GetMapping("/search")
    public CatalogPage search(@RequestParam String q, @RequestParam(defaultValue = "1") int page) {
        return bookService.search(q, page);
    }

    // RS34: detalle publico de un libro. Devuelve 404 si no existe o si su
    // libreria esta deshabilitada (BookService no distingue el motivo).
    @GetMapping("/{id}")
    public BookSummary detail(@PathVariable Long id) {
        return bookService.getDetail(id);
    }
}