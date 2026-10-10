package librosbuysan.library;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.library.LibraryDtos.LibraryResponse;

/**
 * R14/F14: un dueno puede quitar su libreria del sistema. No hay borrado
 * fisico: solo se pone enabled = false (RS34), asi los libros dejan de verse
 * en catalogo/busqueda/detalle sin romper la integridad con los registros
 * que referencian a la libreria.
 */
@Service
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private static final String NOT_FOUND = "Libreria no encontrada";

    private final LibraryRepo libraryRepo;

    public LibraryService(LibraryRepo libraryRepo) {
        this.libraryRepo = libraryRepo;
    }

    @Transactional(readOnly = true)
    public LibraryResponse getOwnLibrary(Long userId) {
        Library library = libraryRepo.findByDuenoIdAndEnabledTrue(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        return LibraryResponse.from(library);
    }

    /**
     * Inexistente, ajena o ya deshabilitada responden el mismo 404: un dueno
     * no puede usar este endpoint para averiguar que ids de libreria existen
     * ni a quien pertenecen.
     */
    @Transactional
    public void disableOwnLibrary(Long userId, Long libraryId) {
        Library library = libraryRepo.findById(libraryId).orElse(null);
        if (library == null || !library.isEnabled() || !library.getDueno().getId().equals(userId)) {
            String reason = library == null ? "inexistente"
                    : !library.isEnabled() ? "ya_deshabilitada" : "no_es_propia";
            log.warn("Baja de libreria rechazada: userId={} libraryId={} motivo={}", userId, libraryId, reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        library.disable();
        libraryRepo.save(library);
        log.info("Libreria dada de baja: userId={} libraryId={}", userId, libraryId);
    }
}
