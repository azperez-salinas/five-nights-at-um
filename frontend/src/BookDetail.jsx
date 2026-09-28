import { useEffect, useState } from 'react';
import Nav from './Nav';
import './Catalog.css';

export default function BookDetail({ bookId, backHref, session }) {
    const [book, setBook] = useState(null);
    // Id whose request failed, with the reason: 'not-found' or 'error'
    const [failure, setFailure] = useState(null);

    useEffect(() => {
        const controller = new AbortController();

        fetch(`http://localhost:8080/api/books/${bookId}`, { signal: controller.signal })
            .then((response) => {
                if (response.status === 404 || response.status === 400) {
                    setFailure({ bookId, reason: 'not-found' });
                    return null;
                }
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((data) => data && setBook(data))
            .catch((err) => {
                if (err.name !== 'AbortError') setFailure({ bookId, reason: 'error' });
            });

        return () => controller.abort();
    }, [bookId]);

    const failed = failure?.bookId === bookId ? failure.reason : null;
    const current = book?.id === bookId ? book : null;

    return (
        <div className="booksaw-page">
            <Nav active="catalogo" session={session} />

            <main className="catalog-main">
                <a href={backHref} className="book-detail-back">← VOLVER AL CATÁLOGO</a>

                {failed === 'not-found' && (
                    <p className="catalog-status">El libro que buscás no existe o no está disponible.</p>
                )}

                {failed === 'error' && (
                    <div className="alert-box alert-error" role="alert">
                        <span>No pudimos cargar el libro. Por favor, intentá nuevamente en unos momentos.</span>
                    </div>
                )}

                {!failed && !current && <p className="catalog-status">Cargando libro...</p>}

                {current && (
                    <article className="book-detail">
                        <img
                            src={current.portadaUrl}
                            alt={`Portada de ${current.titulo}`}
                            referrerPolicy="no-referrer"
                        />
                        <div>
                            <p className="catalog-card-library">{current.nombreLibreria}</p>
                            <h1 className="catalog-title">{current.titulo}</h1>
                            <p className="book-detail-author">{current.autor}</p>
                            <p>{current.descripcion}</p>
                            <dl className="book-detail-meta">
                                <dt>Publicación</dt>
                                <dd>{current.fechaPublicacion}</dd>
                                <dt>Páginas</dt>
                                <dd>{current.cantidadPaginas}</dd>
                                <dt>ISBN</dt>
                                <dd>{current.isbn}</dd>
                            </dl>
                        </div>
                    </article>
                )}
            </main>
        </div>
    );
}
