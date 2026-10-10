package librosbuysan.book;

import java.time.LocalDate;
import librosbuysan.library.Library;

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

    // La libreria se asigna desde el seeder (no viene en el JSON): el JSON
    // solo describe el libro, la distribucion entre librerias de prueba es
    // un detalle de como se arma el dato de ejemplo.
    Book toEntity(Library library) {
        return new Book(library, isbn, titulo, autor, fechaPublicacion, cantidadPaginas, descripcion, portadaUrl);
    }
}