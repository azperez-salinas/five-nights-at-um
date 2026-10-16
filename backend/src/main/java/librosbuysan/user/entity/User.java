package librosbuysan.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
public class User {

    public enum Role {
        COMPRADOR,
        DUENO,
        // R18: solo existe por seed (AdminSeeder); no hay endpoint que lo cree
        ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ColumnDefault("true")
    @Column(nullable = false)
    private boolean enabled = true;

    // R18: los duenos creados por el admin reciben una contrasena temporal y
    // deben cambiarla en su primer inicio de sesion. Se apaga al cambiarla.
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    // RS28: version de las credenciales. Cada token lleva la version vigente al
    // emitirse (claim "tv" en JwtService) y JwtAuthenticationFilter rechaza los
    // que no coinciden con la actual. Al cambiar la contrasena se incrementa, asi
    // que todos los tokens emitidos ANTES del cambio dejan de servir aunque no
    // hayan expirado. Se usa un contador y no un instante porque el iat del JWT
    // tiene precision de segundos: un token emitido en el mismo segundo que el
    // cambio no se podria distinguir.
    @ColumnDefault("0")
    @Column(nullable = false)
    private int tokenVersion = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // requerido por JPA
    }

    /**
     * Constructor de registro publico: el rol y el estado los fija la entidad,
     * nunca el cliente.
     */
    public User(String username, String email, String passwordHash) {
        this(username, email, passwordHash, Role.COMPRADOR);
    }

    public static User dueno(String username, String email, String passwordHash) {
        return new User(username, email, passwordHash, Role.DUENO);
    }

    /**
     * R18: dueno dado de alta por el admin. La contrasena es temporal, asi que
     * queda marcado para cambiarla en el primer inicio de sesion.
     */
    public static User duenoConPasswordTemporal(String username, String email, String passwordHash) {
        User user = new User(username, email, passwordHash, Role.DUENO);
        user.mustChangePassword = true;
        return user;
    }

    // R18: solo lo usa AdminSeeder
    public static User admin(String username, String email, String passwordHash) {
        return new User(username, email, passwordHash, Role.ADMIN);
    }

    private User(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    // Una contrasena elegida por el propio usuario deja de ser temporal (R18).
    // RS28: ademas invalida todos los tokens emitidos con la contrasena anterior.
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        this.tokenVersion++;
    }
}
