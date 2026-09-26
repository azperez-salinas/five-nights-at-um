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

    private final BookRepo bookRepo;

    public BookService(BookRepo bookRepo) {
        this.bookRepo = bookRepo;
    }

    public CatalogPage getCatalog(int page) {
        validate(page);

        // Orden explicito: sin ORDER BY, Postgres no garantiza el mismo orden
        // entre dos consultas paginadas separadas (podria repetir o saltear
        // libros al pasar de una pagina a otra).
        Sort orderById = Sort.by("id").ascending();
        PageRequest pageable = PageRequest.of(page - 1, PAGE_SIZE, orderById);

        Page<Book> result = bookRepo.findAll(pageable);
        List<BookSummary> items = result.getContent().stream().map(BookSummary::from).toList();

        return new CatalogPage(items, page, PAGE_SIZE, result.getTotalElements(), result.getTotalPages());
    }

    private void validate(int page) {
        if (page < 1) {
            log.warn("Solicitud de catalogo invalida: page={}", page);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PAGE);
        }
    }
}