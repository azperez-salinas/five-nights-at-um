package librosbuysan.book.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import librosbuysan.library.entity.Library;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Catalogo de libros disponibles en la plataforma.
 *
 * Cada libreria cataloga sus propios libros como filas independientes: el
 * mismo titulo (mismo ISBN incluido) puede existir en varias librerias a la
 * vez, cada copia con su propio id. Por eso el ISBN ya NO es unique a nivel
 * de tabla (antes si lo era, cuando cada libro pertenecia a una sola
 * libreria del sistema); sigue siendo obligatorio como dato del libro.
 *
 * Esta manera de crear los datos (por script) cambiara en el sprint2
 * ya que el dueño debera cargar los datos por csv
 */
@Entity
@DynamicUpdate
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RS34: todo libro pertenece a una libreria. Las consultas de catalogo,
    // busqueda y detalle usan library.enabled para decidir si el libro sigue
    // siendo visible, aunque el registro del libro nunca se borre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    @Column(nullable = false, length = 20)
    private String isbn;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(nullable = false, length = 150)
    private String autor;

    @Column(name = "fecha_publicacion", nullable = false)
    private LocalDate fechaPublicacion;

    @Column(name = "cantidad_paginas", nullable = false)
    private int cantidadPaginas;

    // 1000 alcanza para una descripcion breve; si en el futuro se permiten
    // descripciones largas, conviene pasar esta columna a @Lob (TEXT).
    @Column(length = 1000)
    private String descripcion;

    @Column(name = "portada_url", length = 500)
    private String portadaUrl;

    // R18/F18: baja logica de UN libro puntual (independiente de que su
    // libreria siga habilitada). Mismo patron que Library.enabled: las
    // consultas de catalogo/busqueda/detalle/mine filtran por este flag, y
    // DELETE /api/books/{id} lo pone en false en vez de borrar la fila, para
    // no romper la integridad con reservas asociadas al libro.
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean enabled = true;

    protected Book() {
        // requerido por JPA
    }

    public Book(Library library, String isbn, String titulo, String autor, LocalDate fechaPublicacion,
                int cantidadPaginas, String descripcion, String portadaUrl) {
        this.library = library;
        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.fechaPublicacion = fechaPublicacion;
        this.cantidadPaginas = cantidadPaginas;
        this.descripcion = descripcion;
        this.portadaUrl = portadaUrl;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public Library getLibrary() {
        return library;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getAutor() {
        return autor;
    }

    public LocalDate getFechaPublicacion() {
        return fechaPublicacion;
    }

    public int getCantidadPaginas() {
        return cantidadPaginas;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getPortadaUrl() {
        return portadaUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // R18/F18: baja logica desde DELETE /api/books/{id}. No borra la fila
    // (se preserva por integridad con reservas asociadas al libro), solo lo
    // oculta de catalogo/busqueda/detalle/mine.
    public void disable() {
        this.enabled = false;
    }

    // R18/F18: unicos campos editables via PUT /api/books/{id} (RS36/RS2/RS7).
    // libreriaId e isEnabled NO tienen setter: no son editables por el
    // cliente, el estado solo cambia por disable().
    public void changeTitulo(String titulo) {
        this.titulo = titulo;
    }

    public void changeAutor(String autor) {
        this.autor = autor;
    }

    public void changeIsbn(String isbn) {
        this.isbn = isbn;
    }

    public void changePortadaUrl(String portadaUrl) {
        this.portadaUrl = portadaUrl;
    }
}