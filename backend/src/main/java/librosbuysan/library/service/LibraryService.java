package librosbuysan.library.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import librosbuysan.favorite.repository.FavoriteRepo;
import librosbuysan.library.dto.LibraryDtos.BookRankingItem;
import librosbuysan.library.dto.LibraryDtos.LibraryMetricsResponse;
import librosbuysan.library.dto.LibraryDtos.LibraryResponse;
import librosbuysan.library.entity.Library;
import librosbuysan.library.repository.LibraryRepo;

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

    // R13/F13: cantidad de filas del ranking del panel de metricas.
    private static final int TOP_RANKING_SIZE = 10;

    private final LibraryRepo libraryRepo;
    private final FavoriteRepo favoriteRepo;

    public LibraryService(LibraryRepo libraryRepo, FavoriteRepo favoriteRepo) {
        this.libraryRepo = libraryRepo;
        this.favoriteRepo = favoriteRepo;
    }

    // R10: librerias habilitadas, para el filtro del catalogo
    @Transactional(readOnly = true)
    public List<LibraryResponse> listEnabled() {
        return libraryRepo.findByEnabledTrueOrderByNombreAsc().stream().map(LibraryResponse::from).toList();
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

    /**
     * R13/F13: panel de metricas del dueno autenticado.
     *
     * R6 (IDOR): reusa findByDuenoIdAndEnabledTrue, el MISMO criterio que
     * getOwnLibrary, para resolver la libreria a partir del userId del token
     * — este metodo no recibe libreriaId por parametro en ningun momento, asi
     * que no hay forma de pedir las metricas de una libreria ajena.
     * RS20/RS24: library.getId() (el atributo "libreriaId") es lo unico que
     * decide que favoritos entran en la agregacion, via
     * FavoriteRepo.countByBookLibraryId.../findTopFavoritedBooks, que filtran
     * por la libreria DEL LIBRO de cada favorito, nunca por nada global.
     * RS5: el resultado queda acotado a esta libreria, nunca a metricas de
     * toda la plataforma.
     */
    @Transactional(readOnly = true)
    public LibraryMetricsResponse getMetrics(Long userId) {
        Library library = libraryRepo.findByDuenoIdAndEnabledTrue(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));

        long totalFavoritos = favoriteRepo
                .countByBookLibraryIdAndBookEnabledTrueAndBookLibraryEnabledTrue(library.getId());

        // RS2/RS7: la proyeccion BookFavoriteCount solo trae datos del libro
        // y la cantidad; se mapea a BookRankingItem para no filtrar la
        // interfaz de persistencia hasta la respuesta HTTP.
        List<BookRankingItem> topLibros = favoriteRepo
                .findTopFavoritedBooks(library.getId(), PageRequest.of(0, TOP_RANKING_SIZE))
                .stream()
                .map(fila -> new BookRankingItem(fila.getBookId(), fila.getTitulo(), fila.getAutor(),
                        fila.getCantidadFavoritos()))
                .toList();

        return new LibraryMetricsResponse(totalFavoritos, topLibros);
    }
}
