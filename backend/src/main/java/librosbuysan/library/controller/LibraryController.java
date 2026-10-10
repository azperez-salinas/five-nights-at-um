package librosbuysan.library;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.library.LibraryDtos.LibraryResponse;
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
}
