package librosbuysan.book;

import java.time.LocalDate;

/**
 * Representa una fila del archivo de datos resources/data/books-seed.json.
 * Se mantiene separado de la entidad Book a proposito: el formato del
 * archivo de carga es un detalle de infraestructura, no del modelo de
 * dominio, y no queremos que un cambio en uno obligue a tocar el otro.
 */
public record BookSeedRecord(
        String isbn,
        String titulo,
        String autor,
        LocalDate fechaPublicacion,
        int cantidadPaginas,
        String descripcion,
        String portadaUrl) {

    Book toEntity() {
        return new Book(isbn, titulo, autor, fechaPublicacion, cantidadPaginas, descripcion, portadaUrl);
    }
}