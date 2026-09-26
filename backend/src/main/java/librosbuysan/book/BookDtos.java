package librosbuysan.book;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs del catalogo (R7). Separados de la entidad Book a proposito: el
 * contrato de la API no deberia romperse cada vez que la tabla "books"
 * cambie (por ejemplo cuando se sume la relacion con Library).
 */
public final class BookDtos {

    private BookDtos() {
    }

    public record BookSummary(
            Long id,
            String isbn,
            String titulo,
            String autor,
            LocalDate fechaPublicacion,
            int cantidadPaginas,
            String descripcion,
            String portadaUrl) {

        public static BookSummary from(Book book) {
            return new BookSummary(
                    book.getId(),
                    book.getIsbn(),
                    book.getTitulo(),
                    book.getAutor(),
                    book.getFechaPublicacion(),
                    book.getCantidadPaginas(),
                    book.getDescripcion(),
                    book.getPortadaUrl());
        }
    }

    /**
     * page y limit son 1-indexados de cara al cliente (page=1 es la primera
     * pagina), aunque Spring Data trabaje internamente 0-indexado. Se
     * devuelven de vuelta en la respuesta para que el frontend no tenga que
     * recordar que pidio.
     */
    public record CatalogPage(
            List<BookSummary> items,
            int page,
            int limit,
            long totalItems,
            int totalPages) {
    }
}