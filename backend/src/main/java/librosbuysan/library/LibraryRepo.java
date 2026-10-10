package librosbuysan.library;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryRepo extends JpaRepository<Library, Long> {

    // R14: la libreria del dueno autenticado (relacion 1 a 1 con User)
    Optional<Library> findByDuenoIdAndEnabledTrue(Long duenoId);
}
