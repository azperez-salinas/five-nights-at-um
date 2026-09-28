import { useEffect, useState } from 'react';
import './Register.css';
import './Catalog.css';

export default function Catalog() {
    const [page, setPage] = useState(1);
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

    const errorMsg = failedPage === page
        ? 'No pudimos cargar el catálogo. Por favor, intentá nuevamente en unos momentos.'
        : '';
    const loading = data?.page !== page && !errorMsg;
    const totalPages = data?.totalPages ?? 1;

    return (
        <div className="booksaw-page">
            <header className="booksaw-nav">
                <div className="booksaw-nav-container">
                    <div className="booksaw-logo">
                        <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                        <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                    </div>
                    <nav className="booksaw-menu">
                        <a href="#inicio" className="nav-item">INICIO</a>
                        <a href="#catalogo" className="nav-item active">CATÁLOGO</a>
                    </nav>
                </div>
            </header>

            <main className="catalog-main">
                <h1 className="catalog-title">Catálogo</h1>

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
                    <ul className="catalog-grid" aria-busy={loading}>
                        {data.items.map((book) => (
                            <li key={book.id} className="catalog-card">
                                <img
                                    src={book.portadaUrl}
                                    alt={`Portada de ${book.titulo}`}
                                    loading="lazy"
                                    referrerPolicy="no-referrer"
                                />
                                <h2 className="catalog-card-title">{book.titulo}</h2>
                                <p className="catalog-card-author">{book.autor}</p>
                                <p className="catalog-card-library">{book.nombreLibreria}</p>
                            </li>
                        ))}
                    </ul>
                )}

                {data && totalPages > 1 && (
                    <nav className="catalog-pagination" aria-label="Paginación del catálogo">
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => setPage((p) => p - 1)}
                            disabled={loading || page <= 1}
                        >
                            ANTERIOR
                        </button>
                        <span>Página {page} de {totalPages}</span>
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => setPage((p) => p + 1)}
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
