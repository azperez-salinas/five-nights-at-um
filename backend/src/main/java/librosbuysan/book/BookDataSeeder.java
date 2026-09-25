package librosbuysan.book;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

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

    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    public BookDataSeeder(BookRepository bookRepository, ObjectMapper objectMapper) {
        this.bookRepository = bookRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        // Idempotencia: si ya hay libros cargados, no repetimos el trabajo
        // ni corremos el riesgo de violar la restriccion unique del ISBN.
        if (bookRepository.count() > 0) {
            log.info("Catalogo de libros ya inicializado, se omite la carga inicial.");
            return;
        }

        List<BookSeedRecord> seedRecords;
        try (InputStream input = new ClassPathResource(SEED_FILE).getInputStream()) {
            seedRecords = objectMapper.readValue(input, new TypeReference<List<BookSeedRecord>>() {});
        }

        List<Book> books = seedRecords.stream().map(BookSeedRecord::toEntity).toList();
        bookRepository.saveAll(books);
        log.info("Catalogo de libros inicializado con {} registros.", books.size());
    }
}