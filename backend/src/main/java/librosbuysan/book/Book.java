package librosbuysan.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Catalogo de libros disponibles en la plataforma.
 *
 * El ISBN es el identificador de negocio (unique, no null): es lo que
 * cualquier integracion externa (o el propio dueno de la libreria) usara
 * para referirse a un libro, por lo que nunca deberia repetirse ni faltar.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
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

    protected Book() {
        // requerido por JPA
    }

    public Book(String isbn, String titulo, String autor, LocalDate fechaPublicacion,
                int cantidadPaginas, String descripcion, String portadaUrl) {
        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.fechaPublicacion = fechaPublicacion;
        this.cantidadPaginas = cantidadPaginas;
        this.descripcion = descripcion;
        this.portadaUrl = portadaUrl;
    }

    public Long getId() {
        return id;
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
}