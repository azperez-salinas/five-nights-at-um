package librosbuysan.book.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import librosbuysan.book.entity.Book;

public interface BookRepo extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    // R10: catalogo con filtros dinamicos (BookSpecs siempre agrega RS34).
    // El EntityGraph trae la libreria en la misma consulta: como el proyecto
    // usa open-in-view=false, la sesion de Hibernate ya esta cerrada cuando
    // BookSummary.from() lee book.getLibrary().getNombre() (R11). Se usa
    // EntityGraph y no JOIN FETCH porque JOIN FETCH rompe la consulta de
    // conteo que Spring Data genera para paginar.
    @Override
    @EntityGraph(attributePaths = "library")
    Page<Book> findAll(Specification<Book> spec, Pageable pageable);

    // RS34: el detalle de un libro de una libreria deshabilitada, o el de un
    // libro dado de baja individualmente (R18), no debe encontrarse (el
    // controller lo traduce a 404), aunque el registro siga existiendo en la
    // base. JOIN FETCH por el mismo motivo que arriba.
    @Query("SELECT b FROM Book b JOIN FETCH b.library l WHERE b.id = :id AND b.enabled = true AND l.enabled = true")
    Optional<Book> findByIdAndEnabledTrueAndLibraryEnabledTrue(@Param("id") Long id);

    // RF9: busqueda por texto libre en titulo, autor o ISBN, respetando
    // siempre RS34 (libreria habilitada y libro habilitado). Se usa @Query
    // con parametro bindeado (:q) en vez de concatenar el texto recibido en
    // el JPQL: el valor del usuario viaja como parametro de la consulta,
    // nunca como texto de la sentencia, evitando inyeccion SQL/JPQL. JOIN
    // FETCH por el mismo motivo que las otras dos consultas.
    @Query("""
            SELECT b FROM Book b JOIN FETCH b.library l
            WHERE b.enabled = true AND l.enabled = true
              AND (LOWER(b.titulo) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.autor) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.isbn) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Book> search(@Param("q") String q, Pageable pageable);

    // R18/F18: catalogo propio del dueno (GET /books/mine). Se filtra por el
    // id de SU libreria (ya resuelta por BookService a partir del token, no
    // de un parametro del request) y por los libros que el aun no dio de
    // baja. EntityGraph trae la libreria en la misma consulta por el mismo
    // motivo que findAll(Specification, Pageable): open-in-view=false.
    @EntityGraph(attributePaths = "library")
    Page<Book> findByLibrary_IdAndEnabledTrue(Long libraryId, Pageable pageable);

    // R18/F18/R6(IDOR): busca un libro puntual DENTRO de la libreria del
    // dueno autenticado. Si el libro no existe, es de otra libreria, o ya
    // esta deshabilitado, el resultado es el mismo Optional vacio — el
    // service no distingue el motivo al responder 404 (RS31/RS38).
    @EntityGraph(attributePaths = "library")
    Optional<Book> findByIdAndLibrary_IdAndEnabledTrue(Long id, Long libraryId);
}