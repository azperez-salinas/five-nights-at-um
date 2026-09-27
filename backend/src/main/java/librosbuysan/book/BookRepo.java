package librosbuysan.book;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepo extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);
}