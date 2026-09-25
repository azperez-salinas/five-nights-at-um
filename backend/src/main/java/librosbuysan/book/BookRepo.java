package librosbuysan.book;

import org.springframework.data.jpa.repository.JpaRepo;

public interface BookRepo extends JpaRepo<Book, Long> {

    boolean existsByIsbn(String isbn);
}