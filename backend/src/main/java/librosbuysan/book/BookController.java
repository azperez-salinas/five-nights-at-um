package librosbuysan.book;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de solo lectura, pensado para verificar la carga masiva.
 * Las funcionalidades de negocio (busqueda, alta por el dueno, etc.) se
 * suman despues sobre este mismo paquete.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookRepository bookRepository;

    public BookController(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GetMapping
    public Page<Book> list(Pageable pageable) {
        return bookRepository.findAll(pageable);
    }
}