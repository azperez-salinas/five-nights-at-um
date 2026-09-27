package librosbuysan.book;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepo extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    // RS34: el catalogo solo muestra libros de librerias habilitadas.
    // Spring Data resuelve "LibraryEnabled" como la propiedad anidada
    // library.enabled sin necesidad de escribir la query a mano.
    Page<Book> findByLibraryEnabledTrue(Pageable pageable);

    // RS34: el detalle de un libro de una libreria deshabilitada no debe
    // encontrarse (el controller lo traduce a 404), aunque el registro siga
    // existiendo en la base.
    Optional<Book> findByIdAndLibraryEnabledTrue(Long id);

    // RF9: busqueda por texto libre en titulo, autor o ISBN, respetando
    // siempre RS34 (libreria habilitada). Se usa @Query con parametro
    // bindeado (:q) en vez de concatenar el texto recibido en el JPQL: el
    // valor del usuario viaja como parametro de la consulta, nunca como
    // texto de la sentencia, evitando inyeccion SQL/JPQL.
    @Query("""
            SELECT b FROM Book b
            WHERE b.library.enabled = true
              AND (LOWER(b.titulo) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.autor) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.isbn) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Book> search(@Param("q") String q, Pageable pageable);
}