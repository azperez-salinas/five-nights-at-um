package librosbuysan.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import librosbuysan.user.User;
import org.hibernate.annotations.ColumnDefault;

/**
 * Una libreria pertenece a un unico dueno (relacion 1 a 1).
 *
 * Se modela como entidad propia y no como una columna extra en User porque
 * "librosbuysan" es una plataforma de librerias: mas adelante la libreria va
 * a ser el punto al que se cuelgan cosas como el stock de libros en venta,
 * direccion, horarios, etc. Meter todo eso en la tabla users mezclaria datos
 * de autenticacion (username, hash de password) con datos de negocio, y
 * ademas dejaria esas columnas siempre en null para los usuarios COMPRADOR.
 */
@Entity
@Table(name = "librarys")
public class Library {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User dueno;

    @Column(nullable = false, length = 150)
    private String nombre;

    // RS34: cuando una libreria se da de baja, sus libros deben dejar de
    // verse en catalogo/busqueda/detalle sin borrar ningun registro. Este
    // flag es lo que las consultas de Book van a chequear para decidir si
    // un libro sigue siendo visible.
    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Library() {
        // requerido por JPA
    }

    public Library(User dueno, String nombre) {
        this.dueno = dueno;
        this.nombre = nombre;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getDueno() {
        return dueno;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // RS34: dar de baja una libreria. No borra el registro ni el de sus
    // libros: solo hace que dejen de ser visibles via catalogo/busqueda/detalle.
    public void disable() {
        this.enabled = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}