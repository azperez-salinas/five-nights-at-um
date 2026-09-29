import { useEffect, useState } from 'react';
import BookCover from './BookCover';
import Nav from './Nav';
import './Catalog.css';

const FAVORITES_URL = 'http://localhost:8080/api/favorites';

// The page lives in the URL (#catalogo/N) so returning from a book detail keeps it
export default function Catalog({ page, session }) {
    const [data, setData] = useState(null);
    // Page number whose request failed; the error only shows for that page
    const [failedPage, setFailedPage] = useState(null);

    useEffect(() => {
        const controller = new AbortController();

        fetch(`http://localhost:8080/api/books?page=${page}`, { signal: controller.signal })
            .then((response) => {
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then(setData)
            .catch((err) => {
                if (err.name !== 'AbortError') setFailedPage(page);
            });

        return () => controller.abort();
    }, [page]);

    // Favorites are a COMPRADOR-only feature (the backend answers 403 to DUENO)
    const token = session?.token;
    const isComprador = session?.role === 'COMPRADOR';
    const [favoriteIds, setFavoriteIds] = useState(() => new Set());
    const [pendingId, setPendingId] = useState(null);
    const [favError, setFavError] = useState('');

    useEffect(() => {
        if (!isComprador) return;
        const controller = new AbortController();

        fetch(FAVORITES_URL, {
            headers: { Authorization: `Bearer ${token}` },
            signal: controller.signal,
        })
            .then((response) => (response.ok ? response.json() : []))
            .then((books) => setFavoriteIds(new Set(books.map((b) => b.id))))
            .catch(() => {});

        return () => controller.abort();
    }, [isComprador, token]);

    const toggleFavorite = async (book) => {
        const isFav = favoriteIds.has(book.id);
        setFavError('');
        setPendingId(book.id);
        try {
            const response = await fetch(`${FAVORITES_URL}/${book.id}`, {
                method: isFav ? 'DELETE' : 'POST',
                headers: { Authorization: `Bearer ${token}` },
            });
            if (!response.ok) throw new Error();
            setFavoriteIds((prev) => {
                const next = new Set(prev);
                if (isFav) next.delete(book.id);
                else next.add(book.id);
                return next;
            });
        } catch {
            setFavError(isFav
                ? 'No pudimos quitar el libro de favoritos. Intentalo de nuevo.'
                : 'No pudimos agregar el libro a favoritos. Intentalo de nuevo.');
        } finally {
            setPendingId(null);
        }
    };

    const errorMsg = failedPage === page
        ? 'No pudimos cargar el catálogo. Por favor, intentá nuevamente en unos momentos.'
        : favError;
    const loading = data?.page !== page && !errorMsg;
    const totalPages = data?.totalPages ?? 1;

    return (
        <div className="booksaw-page">
            <Nav active="catalogo" session={session} />

            <main className="catalog-main">
                <h1 className="catalog-title">Recomendados</h1>

                {errorMsg && (
                    <div className="alert-box alert-error" role="alert">
                        <span>{errorMsg}</span>
                    </div>
                )}

                {loading && !data && <p className="catalog-status">Cargando libros...</p>}

                {data && data.items.length === 0 && (
                    <p className="catalog-status">No hay libros disponibles por el momento.</p>
                )}

                {data && (
                    <div className="editorial-books-grid" aria-busy={loading}>
                        {data.items.map((book) => {
                            const fav = favoriteIds.has(book.id);
                            return (
                                <article key={book.id} className="book-card-item">
                                    <a href={`#libro/${book.id}`} className="catalog-card-link">
                                        <BookCover book={book} />
                                    </a>
                                    <div className="book-card-caption">
                                        <a href={`#libro/${book.id}`} className="caption-text catalog-card-link">
                                            <h3 className="caption-title">{book.titulo}</h3>
                                            <p className="caption-author">{book.autor}</p>
                                        </a>
                                        {isComprador && (
                                            <button
                                                type="button"
                                                className="caption-heart-btn"
                                                onClick={() => toggleFavorite(book)}
                                                disabled={pendingId === book.id}
                                                aria-pressed={fav}
                                                title={fav ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                                                aria-label={fav ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                                            >
                                                <svg viewBox="0 0 24 24" width="19" height="19" fill={fav ? '#A68658' : 'none'} stroke={fav ? '#A68658' : '#8C827A'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                                    <path d="M12 6.8 C10.8 4.2 7.8 2.6 4.8 3.6 C1.8 4.6 1.2 8.8 3 12.2 C5 16 11.2 20.8 12 21.4 C12.8 20.8 19 16 21 12.2 C22.8 8.8 22.2 4.6 19.2 3.6 C16.2 2.6 13.2 4.2 12 6.8 Z" />
                                                </svg>
                                            </button>
                                        )}
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                )}

                {data && totalPages > 1 && (
                    <nav className="catalog-pagination" aria-label="Paginación del catálogo">
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => { window.location.hash = `#catalogo/${page - 1}`; }}
                            disabled={loading || page <= 1}
                        >
                            ANTERIOR
                        </button>
                        <span>Página {page} de {totalPages}</span>
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => { window.location.hash = `#catalogo/${page + 1}`; }}
                            disabled={loading || page >= totalPages}
                        >
                            SIGUIENTE
                        </button>
                    </nav>
                )}
            </main>
        </div>
    );
}
