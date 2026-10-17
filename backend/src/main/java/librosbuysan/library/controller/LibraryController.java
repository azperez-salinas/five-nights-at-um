package librosbuysan.library.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.library.dto.LibraryDtos.LibraryMetricsResponse;
import librosbuysan.library.dto.LibraryDtos.LibraryResponse;
import librosbuysan.library.service.LibraryService;
import librosbuysan.security.AuthenticatedUser;

/**
 * R14: endpoints del dueno sobre su propia libreria. La restriccion a rol
 * DUENO vive en SecurityConfig; la verificacion de que la libreria sea
 * propia, en LibraryService.
 */
@RestController
@RequestMapping("/api/users/libreria")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    // El frontend necesita el id de la libreria propia para poder darla de baja
    @GetMapping
    public LibraryResponse getOwn(@AuthenticationPrincipal AuthenticatedUser principal) {
        return libraryService.getOwnLibrary(principal.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthenticatedUser principal,
                                        @PathVariable Long id) {
        libraryService.disableOwnLibrary(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    // R13/F13/RS1/RS5/RS20/R6(IDOR): panel de metricas, exclusivo del DUENO
    // (restriccion de rol en SecurityConfig). El id de libreria jamas viene
    // de un parametro de la request: sale siempre de principal.id(), mismo
    // criterio que getOwn() arriba y que GET /api/books/mine.
    @GetMapping("/metricas")
    public LibraryMetricsResponse metrics(@AuthenticationPrincipal AuthenticatedUser principal) {
        return libraryService.getMetrics(principal.id());
    }
}
