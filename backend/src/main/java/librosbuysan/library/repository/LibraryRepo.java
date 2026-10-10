package librosbuysan.library;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryRepo extends JpaRepository<Library, Long> {

    // R14: la libreria del dueno autenticado (relacion 1 a 1 con User)
    Optional<Library> findByDuenoIdAndEnabledTrue(Long duenoId);

    // R10: opciones del filtro por libreria del catalogo
    List<Library> findByEnabledTrueOrderByNombreAsc();
}
