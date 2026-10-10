package librosbuysan.book;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
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

        // Librerias de prueba para poder ejercitar RS34: dos habilitadas y
        // una deshabilitada. Cada libreria cataloga el catalogo COMPLETO del
        // seed como filas propias (mismo isbn/titulo/autor/etc., id
        // distinto): las librerias no comparten filas de Book entre si, asi
        // que un libro de una libreria deshabilitada sigue existiendo igual
        // en las habilitadas bajo un id distinto, lo que permite probar que
        // RS34 filtra por libreria y no por contenido del libro.
        Library libreriaA = createSeedLibrary("libreria.central.seed", "libreria.central.seed@example.test",
                "Libreria Central (seed)");
        Library libreriaB = createSeedLibrary("libreria.norte.seed", "libreria.norte.seed@example.test",
                "Libreria Norte (seed)");
        Library libreriaDeshabilitada = createSeedLibrary("libreria.baja.seed", "libreria.baja.seed@example.test",
                "Libreria Dada de Baja (seed)");
        libreriaDeshabilitada.disable();
        libraryRepo.save(libreriaDeshabilitada);

        List<Library> librerias = List.of(libreriaA, libreriaB, libreriaDeshabilitada);

        // Una copia de cada libro del seed por cada libreria: el isbn ya no
        // es unique en la tabla (ver Book.java), asi que puede repetirse
        // entre las copias de distintas librerias sin violar ninguna
        // restriccion.
        List<Book> books = librerias.stream()
                .flatMap(library -> seedRecords.stream().map(record -> record.toEntity(library)))
                .toList();

        bookRepo.saveAll(books);
        log.info("Catalogo de libros inicializado con {} registros ({} librerias x {} libros cada una).",
                books.size(), librerias.size(), seedRecords.size());
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