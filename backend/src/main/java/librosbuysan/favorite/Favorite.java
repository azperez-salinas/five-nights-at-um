package librosbuysan.favorite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import librosbuysan.book.Book;
import librosbuysan.user.User;

/**
 * RF12: relacion entre un usuario (COMPRADOR) y un libro que marco como
 * favorito. Es la tabla usuarioID-libroID que pide el ticket.
 *
 * La unique constraint (user_id, book_id) es lo que garantiza a nivel de
 * base de datos que un mismo libro no pueda quedar duplicado en los
 * favoritos de un usuario, incluso ante dos POST concurrentes.
 */
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RS24: el usuario dueno del favorito. Este es el atributo del recurso
    // que se compara contra el usuario del token en cada operacion (ver
    // FavoriteService): el rol COMPRADOR abre la funcionalidad, pero es
    // esta relacion la que decide si UN favorito puntual es accesible.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {
        // requerido por JPA
    }

    public Favorite(User user, Book book) {
        this.user = user;
        this.book = book;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Book getBook() {
        return book;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
