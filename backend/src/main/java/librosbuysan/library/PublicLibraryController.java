package librosbuysan.library;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.library.LibraryDtos.LibraryResponse;

/**
 * R10: listado publico de librerias habilitadas (solo id y nombre), para el
 * filtro por libreria del catalogo.
 */
@RestController
@RequestMapping("/api/librerias")
public class PublicLibraryController {

    private final LibraryService libraryService;

    public PublicLibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<LibraryResponse> list() {
        return libraryService.listEnabled();
    }
}
