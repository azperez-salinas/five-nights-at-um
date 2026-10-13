package librosbuysan.library.dto;

import java.util.List;
import librosbuysan.library.entity.Library;

public final class LibraryDtos {

    private LibraryDtos() {
    }

    // RS2/RS7: solo id y nombre, nunca el dueno ni datos internos
    public record LibraryResponse(Long id, String nombre) {

        public static LibraryResponse from(Library library) {
            return new LibraryResponse(library.getId(), library.getNombre());
        }
    }

    /**
     * R13/F13: respuesta del panel de metricas del dueno.
     *
     * RS2/RS7: solo expone cantidades y el ranking de libros, nunca datos de
     * los compradores (usuario, email) ni el pedido/favorito completo. RS5:
     * ambos campos ya vienen acotados a la libreria del dueno (ver
     * LibraryService.getMetrics), nunca a metricas globales de la plataforma.
     */
    public record LibraryMetricsResponse(long totalFavoritos, List<BookRankingItem> topLibros) {
    }

    /**
     * Una fila del ranking "libros mas favoriteados" de R13/F13. Solo los
     * campos que el frontend necesita para graficar (titulo, autor y la
     * cantidad), sin exponer qué usuarios lo favoritearon.
     */
    public record BookRankingItem(Long bookId, String titulo, String autor, long cantidadFavoritos) {
    }
}
