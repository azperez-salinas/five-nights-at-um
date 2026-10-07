package librosbuysan.support;

import java.time.LocalDate;
import librosbuysan.auth.JwtService;
import librosbuysan.auth.PasswordHasher;
import librosbuysan.book.Book;
import librosbuysan.book.BookDataSeeder;
import librosbuysan.book.BookRepo;
import librosbuysan.favorite.FavoriteRepo;
import librosbuysan.library.Library;
import librosbuysan.library.LibraryRepo;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de los tests de integracion (R37).
 *
 * Levanta la aplicacion COMPLETA (SecurityFilterChain real, JwtAuthenticationFilter
 * real, controllers, services, repositorios sobre H2). A proposito no se usa
 * @WithMockUser: ese atajo se salta el filtro JWT, y R37 pide justamente
 * comprobar que la verificacion de autorizacion ocurre antes de ejecutar la
 * operacion. Aca los tests mandan un "Authorization: Bearer ..." real,
 * generado por el JwtService real.
 *
 * Escenario que se recrea antes de CADA test (asi ningun test depende del
 * orden ni de datos que dejo otro):
 *
 *   compradorA, compradorB            -> usuarios COMPRADOR
 *   duenoCentral / duenoNorte / duenoBaja -> usuarios DUENO, uno por libreria
 *   central, norte                    -> librerias habilitadas
 *   baja                              -> libreria deshabilitada (RS34)
 *   libroCentral, libroNorte, libroBaja -> MISMO titulo/ISBN, una copia por
 *                                          libreria (asi se ve que el filtro
 *                                          es por libreria y no por contenido)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Password123!";

    // bcrypt con costo 12 tarda ~300 ms por hash. Se calcula UNA vez para toda
    // la corrida y se reutiliza en todos los usuarios de prueba; si no, cada
    // test pagaria 5 hashes y la suite tardaria minutos.
    private static String passwordHash;

    // El seeder real cargaria 606 libros y 3 librerias en cada arranque de
    // contexto y contaminaria los datos controlados de estos tests.
    @MockitoBean
    protected BookDataSeeder bookDataSeeder;

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepo userRepo;
    @Autowired protected LibraryRepo libraryRepo;
    @Autowired protected BookRepo bookRepo;
    @Autowired protected FavoriteRepo favoriteRepo;
    @Autowired protected JwtService jwtService;

    protected User compradorA;
    protected User compradorB;
    protected User duenoCentral;
    protected User duenoNorte;
    protected User duenoBaja;
    protected Library central;
    protected Library norte;
    protected Library baja;
    protected Book libroCentral;
    protected Book libroNorte;
    protected Book libroBaja;

    @BeforeAll
    protected static void calcularHashDeLaPasswordUnaVez() {
        if (passwordHash == null) {
            passwordHash = new PasswordHasher().hash(PASSWORD.toCharArray());
        }
    }

    @BeforeEach
    protected void crearEscenario() {
        // Orden inverso a las claves foraneas
        favoriteRepo.deleteAllInBatch();
        bookRepo.deleteAllInBatch();
        libraryRepo.deleteAllInBatch();
        userRepo.deleteAllInBatch();

        compradorA = userRepo.saveAndFlush(new User("comprador_a", "comprador_a@test.local", passwordHash));
        compradorB = userRepo.saveAndFlush(new User("comprador_b", "comprador_b@test.local", passwordHash));

        duenoCentral = userRepo.saveAndFlush(User.dueno("dueno_central", "dueno_central@test.local", passwordHash));
        duenoNorte = userRepo.saveAndFlush(User.dueno("dueno_norte", "dueno_norte@test.local", passwordHash));
        duenoBaja = userRepo.saveAndFlush(User.dueno("dueno_baja", "dueno_baja@test.local", passwordHash));

        central = libraryRepo.saveAndFlush(new Library(duenoCentral, "Libreria Central"));
        norte = libraryRepo.saveAndFlush(new Library(duenoNorte, "Libreria Norte"));
        Library bajaNueva = new Library(duenoBaja, "Libreria Baja");
        bajaNueva.disable();
        baja = libraryRepo.saveAndFlush(bajaNueva);

        libroCentral = bookRepo.saveAndFlush(quijote(central));
        libroNorte = bookRepo.saveAndFlush(quijote(norte));
        libroBaja = bookRepo.saveAndFlush(quijote(baja));
    }

    protected static Book quijote(Library library) {
        return new Book(library, "9789900000013", "Don Quijote de la Mancha", "Miguel de Cervantes",
                LocalDate.of(1605, 1, 1), 863, "Un hidalgo enloquecido por las novelas de caballerias.", null);
    }

    /** Valor listo para el header Authorization, con un JWT real del usuario. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
