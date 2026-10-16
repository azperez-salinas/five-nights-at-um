import { useEffect, useState } from 'react';

const METRICS_URL = 'http://localhost:8080/api/users/libreria/metricas';

// R13: owner's library metrics (favorites stand in for reservations)
export default function LibraryMetrics({ token, onUnauthorized, refreshKey }) {
    const [metrics, setMetrics] = useState(null);
    // 'loading' | 'ready' | 'none' | 'error'
    const [status, setStatus] = useState('loading');

    useEffect(() => {
        const controller = new AbortController();

        fetch(METRICS_URL, {
            headers: { Authorization: `Bearer ${token}` },
            signal: controller.signal,
        })
            .then((response) => {
                if (response.status === 401) {
                    onUnauthorized();
                    return null;
                }
                // 404: no active library, MyLibrary already explains it
                if (response.status === 404) {
                    setStatus('none');
                    return null;
                }
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((data) => {
                if (data) {
                    setMetrics(data);
                    setStatus('ready');
                }
            })
            .catch((err) => {
                if (err.name !== 'AbortError') setStatus('error');
            });

        return () => controller.abort();
    }, [token, onUnauthorized, refreshKey]);

    if (status === 'none') return null;

    const top = metrics?.topLibros ?? [];
    const max = Math.max(1, ...top.map((b) => b.cantidadFavoritos));

    return (
        <section className="library-metrics" aria-labelledby="library-metrics-title">
            <h2 id="library-metrics-title" className="csv-import-title">Métricas</h2>

            {status === 'loading' && <p className="card-subtitle">Cargando métricas...</p>}
            {status === 'error' && <p className="card-subtitle">No pudimos cargar las métricas.</p>}

            {status === 'ready' && (
                <div className="library-metrics-grid">
                    <div className="metric-tile">
                        <p className="metric-tile-label">Favoritos totales</p>
                        <p className="metric-tile-value">{metrics.totalFavoritos}</p>
                        <p className="metric-tile-hint">
                            {top.length === 1 ? 'en 1 libro' : `en ${top.length} libros`}
                        </p>
                    </div>

                    <div className="metric-ranking">
                        <h3 className="metric-ranking-title">Libros más guardados en favoritos</h3>
                        {top.length === 0 ? (
                            <p className="card-subtitle">Todavía ningún comprador guardó tus libros en favoritos.</p>
                        ) : (
                            <ol className="metric-bars">
                                {top.map((book) => (
                                    <li
                                        key={book.bookId}
                                        className="metric-bar-row"
                                        title={`${book.titulo} — ${book.cantidadFavoritos} favoritos`}
                                    >
                                        <span className="metric-bar-label">
                                            <span className="metric-bar-title">{book.titulo}</span>
                                            <span className="metric-bar-author">{book.autor}</span>
                                        </span>
                                        <span className="metric-bar-track">
                                            <span
                                                className="metric-bar"
                                                style={{ width: `${(book.cantidadFavoritos / max) * 100}%` }}
                                            />
                                            <span className="metric-bar-value">{book.cantidadFavoritos}</span>
                                        </span>
                                    </li>
                                ))}
                            </ol>
                        )}
                    </div>
                </div>
            )}
        </section>
    );
}
