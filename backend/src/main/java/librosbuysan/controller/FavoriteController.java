package librosbuysan.favorite;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import librosbuysan.book.BookDtos.BookSummary;
import librosbuysan.favorite.FavoriteService.AddResult;
import librosbuysan.security.AuthenticatedUser;

/**
 * RF12: alta/baja/listado de la lista de favoritos del COMPRADOR
 * autenticado.
 *
 * Ninguno de los tres metodos recibe un body con el usuario: el "dueno" del
 * favorito sale siempre de AuthenticatedUser (extraido del JWT por
 * JwtAuthenticationFilter), nunca de un campo enviado por el cliente
 * (RS36) — asi es imposible que un comprador manipule favoritos de otro
 * usuario cambiando un id en el request.
 *
 * Los tres son metodos HTTP especificos (@GetMapping/@PostMapping/
 * @DeleteMapping): igual que en BookController, cualquier otro verbo sobre
 * estas rutas recibe 405 de Spring automaticamente, sin llegar al service
 * (RS33). El acceso en si (que solo un COMPRADOR autenticado pueda llamar a
 * estas rutas) se restringe en SecurityConfig, no aca (RS1/RS5/RS9/RS19).
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<BookSummary> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return favoriteService.list(principal.id());
    }

    @PostMapping("/{bookId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal AuthenticatedUser principal,
                                    @PathVariable Long bookId) {
        AddResult result = favoriteService.add(principal.id(), bookId);
        // 201 si se creo un favorito nuevo, 200 si ya existia (idempotente,
        // no es un error volver a marcar como favorito algo que ya lo era).
        HttpStatus status = (result == AddResult.CREATED) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).build();
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable Long bookId) {
        favoriteService.remove(principal.id(), bookId);
        return ResponseEntity.noContent().build();
    }
}
