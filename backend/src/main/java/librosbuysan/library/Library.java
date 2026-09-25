package librosbuysan.libreria;

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
@Table(name = "librerias")
public class Libreria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User dueno;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Libreria() {
        // requerido por JPA
    }

    public Libreria(User dueno, String nombre) {
        this.dueno = dueno;
        this.nombre = nombre;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}