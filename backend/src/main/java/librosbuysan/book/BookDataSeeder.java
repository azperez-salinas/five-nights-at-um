package librosbuysan.book;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import librosbuysan.auth.PasswordHasher;
import librosbuysan.library.Library;
import librosbuysan.library.LibraryRepo;
import librosbuysan.user.User;
import librosbuysan.user.UserRepo;

/**
 * Carga el catalogo inicial de libros desde resources/data/books-seed.json
 * la primera vez que la aplicacion arranca contra una base vacia.
 *
 * Por que un CommandLineRunner y no un data.sql:
 * - Spring solo ejecuta data.sql automaticamente contra bases embebidas
 *   (H2, etc.); contra una base externa como esta (Postgres en Docker) hay
 *   que activarlo a mano con spring.sql.init.mode=always, y en ese modo
 *   Spring lo vuelve a ejecutar en CADA arranque, lo que rompe si el ISBN
 *   es unique (fallaria por clave duplicada) salvo que se agreguen
 *   ON CONFLICT en todas las sentencias.
 * - Este runner controla explicitamente cuando cargar (solo si la tabla
 *   esta vacia), separa el "dato" (JSON) del "codigo" (esta clase) y usa
 *   el mismo ObjectMapper/Jackson que ya trae spring-boot-starter-web,
 *   sin agregar dependencias nuevas al proyecto.
 */
@Component
public class BookDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BookDataSeeder.class);
    private static final String SEED_FILE = "data/Bookseed.json";

    // Cuantos libros del final de la lista van a la libreria deshabilitada
    // de prueba. El resto se reparte entre las librerias habilitadas. Un
    // numero chico y facil de reconocer para poder verificar a mano el
    // filtro de RS34 (esos libros no deben aparecer en catalogo/busqueda).
    private static final int BOOKS_FOR_DISABLED_LIBRARY = 15;

    private final BookRepo bookRepo;
    private final LibraryRepo libraryRepo;
    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final ObjectMapper objectMapper;

    public BookDataSeeder(BookRepo bookRepo, LibraryRepo libraryRepo, UserRepo userRepo,
                          PasswordHasher passwordHasher, ObjectMapper objectMapper) {
        this.bookRepo = bookRepo;
        this.libraryRepo = libraryRepo;
        this.userRepo = userRepo;
        this.passwordHasher = passwordHasher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        // Idempotencia: si ya hay libros cargados, no repetimos el trabajo
        // ni corremos el riesgo de violar la restriccion unique del ISBN.
        if (bookRepo.count() > 0) {
            log.info("Catalogo de libros ya inicializado, se omite la carga inicial.");
            return;
        }

        List<BookSeedRecord> seedRecords;
        try (InputStream input = new ClassPathResource(SEED_FILE).getInputStream()) {
            seedRecords = objectMapper.readValue(input, new TypeReference<List<BookSeedRecord>>() {});
        }

        // Librerias de prueba para poder ejercitar RS34: dos habilitadas
        // (donde va la mayoria del catalogo, repartido round-robin) y una
        // deshabilitada (con un grupo chico de libros) para verificar que
        // sus libros dejan de verse en catalogo/busqueda/detalle.
        Library libreriaA = createSeedLibrary("libreria.central.seed", "libreria.central.seed@example.test",
                "Libreria Central (seed)");
        Library libreriaB = createSeedLibrary("libreria.norte.seed", "libreria.norte.seed@example.test",
                "Libreria Norte (seed)");
        Library libreriaDeshabilitada = createSeedLibrary("libreria.baja.seed", "libreria.baja.seed@example.test",
                "Libreria Dada de Baja (seed)");
        libreriaDeshabilitada.disable();
        libraryRepo.save(libreriaDeshabilitada);

        int total = seedRecords.size();
        int enabledCount = Math.max(total - BOOKS_FOR_DISABLED_LIBRARY, 0);

        // IntStream.range en vez de stream().map(record -> indexOf(record)):
        // los BookSeedRecord son records (igualdad por valor), asi que si dos
        // filas del JSON tuvieran el mismo contenido, indexOf devolveria
        // siempre la primera coincidencia y asignaria mal la libreria.
        List<Book> books = IntStream.range(0, total)
                .mapToObj(index -> {
                    BookSeedRecord record = seedRecords.get(index);
                    if (index >= enabledCount) {
                        return record.toEntity(libreriaDeshabilitada);
                    }
                    // Round-robin entre las dos librerias habilitadas.
                    Library library = (index % 2 == 0) ? libreriaA : libreriaB;
                    return record.toEntity(library);
                })
                .toList();

        bookRepo.saveAll(books);
        log.info("Catalogo de libros inicializado con {} registros ({} en libreria deshabilitada de prueba).",
                books.size(), total - enabledCount);
    }

    /**
     * Crea un usuario DUENO placeholder (sin credenciales pensadas para
     * login real, solo para poder cumplir la relacion obligatoria
     * Library.dueno) y su libreria asociada.
     */
    private Library createSeedLibrary(String username, String email, String nombreLibreria) {
        // Password aleatoria: nadie necesita loguearse con este usuario, solo
        // existe para satisfacer la relacion Library-User. Se genera con
        // SecureRandom (no un valor fijo) para no dejar una credencial
        // adivinable en ningun ambiente, y se hashea una sola vez.
        User dueno = User.dueno(username, email, passwordHasher.hash(randomPlaceholderPassword()));
        dueno = userRepo.save(dueno);
        Library library = new Library(dueno, nombreLibreria);
        return libraryRepo.save(library);
    }

    private char[] randomPlaceholderPassword() {
        byte[] randomBytes = new byte[24];
        new SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getEncoder().encodeToString(randomBytes).toCharArray();
    }
}