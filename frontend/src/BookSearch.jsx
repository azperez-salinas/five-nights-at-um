import React, { useState, useEffect } from 'react';
import './BookSearch.css';

export default function BookSearch({ token, currentUser, onLogout, onNavigate }) {
    const [searchTerm, setSearchTerm] = useState('');
    const [books, setBooks] = useState([]);
    const [allCatalog, setAllCatalog] = useState([]);
    const [favorites, setFavorites] = useState([]);
    const [loading, setLoading] = useState(false);
    const [toastMsg, setToastMsg] = useState('');

    const showToast = (msg) => {
        setToastMsg(msg);
        setTimeout(() => setToastMsg(''), 3000);
    };

    // Carga lista de favoritos del comprador
    const fetchFavorites = async () => {
        if (!token) return;
        try {
            const res = await fetch('http://localhost:8080/api/favorites', {
                headers: { 'Authorization': `Bearer ${token}` }
            });
            if (res.ok) {
                const data = await res.json();
                setFavorites(data);
            } else if (res.status === 401) {
                onLogout();
            }
        } catch (err) {
            console.error(err);
        }
    };

    // Carga catálogo inicial
    const fetchInitialCatalog = async () => {
        setLoading(true);
        try {
            const res = await fetch('http://localhost:8080/api/books?page=1');
            if (res.ok) {
                const data = await res.json();
                const items = data.items || data || [];
                setAllCatalog(items);
                setBooks(items);
            }
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchInitialCatalog();
        fetchFavorites();
    }, [token]);

    // Ejecutar búsqueda por texto libre
    const executeSearch = async (queryText) => {
        const cleanQuery = queryText.trim();
        if (!cleanQuery) {
            setBooks(allCatalog);
            return;
        }

        setLoading(true);
        try {
            const encoded = encodeURIComponent(cleanQuery);

            // Intentar endpoint de búsqueda
            let res = await fetch(`http://localhost:8080/api/books/search?q=${encoded}`);

            if (!res.ok && res.status === 404) {
                res = await fetch(`http://localhost:8080/api/books?search=${encoded}`);
            }
            if (!res.ok && res.status === 404) {
                res = await fetch(`http://localhost:8080/api/books?q=${encoded}`);
            }

            if (res.ok) {
                const data = await res.json();
                const items = data.items || data || [];
                setBooks(items);
            } else {
                // Fallback defensivo en frontend
                const lower = cleanQuery.toLowerCase();
                const filtered = allCatalog.filter((b) =>
                    (b.titulo && b.titulo.toLowerCase().includes(lower)) ||
                    (b.autor && b.autor.toLowerCase().includes(lower)) ||
                    (b.isbn && b.isbn.toLowerCase().includes(lower))
                );
                setBooks(filtered);
            }
        } catch (err) {
            console.error(err);
            const lower = cleanQuery.toLowerCase();
            const filtered = allCatalog.filter((b) =>
                (b.titulo && b.titulo.toLowerCase().includes(lower)) ||
                (b.autor && b.autor.toLowerCase().includes(lower)) ||
                (b.isbn && b.isbn.toLowerCase().includes(lower))
            );
            setBooks(filtered);
        } finally {
            setLoading(false);
        }
    };

    const handleInputChange = (e) => {
        const val = e.target.value;
        setSearchTerm(val);
        executeSearch(val);
    };

    const handleClearSearch = () => {
        setSearchTerm('');
        setBooks(allCatalog);
    };

    const isFavorite = (bookId) => favorites.some((f) => f.id === bookId);

    const handleToggleFavorite = async (book) => {
        if (!token) {
            showToast('Iniciá sesión para guardar favoritos');
            return;
        }
        const isFav = isFavorite(book.id);

        if (isFav) {
            try {
                const res = await fetch(`http://localhost:8080/api/favorites/${book.id}`, {
                    method: 'DELETE',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (res.ok || res.status === 204) {
                    setFavorites((prev) => prev.filter((f) => f.id !== book.id));
                    showToast(`"${book.titulo}" quitado de favoritos`);
                }
            } catch (err) {
                showToast('Error al quitar de favoritos');
            }
        } else {
            try {
                const res = await fetch(`http://localhost:8080/api/favorites/${book.id}`, {
                    method: 'POST',
                    headers: { 'Authorization': `Bearer ${token}` }
                });
                if (res.ok || res.status === 201) {
                    setFavorites((prev) => [...prev, book]);
                    showToast(`"${book.titulo}" agregado a favoritos`);
                }
            } catch (err) {
                showToast('Error al agregar a favoritos');
            }
        }
    };

    return (
        <div className="booksaw-page">
            {/* 1. Header / Navbar BookSaw */}
            <header className="booksaw-nav">
                <div className="booksaw-nav-container">
                    <div className="booksaw-logo" onClick={() => onNavigate('inicio')} style={{ cursor: 'pointer' }}>
                        <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                        <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                    </div>

                    <nav className="booksaw-menu">
                        <a href="#inicio" className="nav-item" onClick={() => onNavigate('inicio')}>INICIO</a>
                        <a href="#buscar" className="nav-item nav-search-item active">
                            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="11" cy="11" r="7.5"></circle>
                                <line x1="21" y1="21" x2="16.5" y2="16.5"></line>
                            </svg>
                            <span>BUSCAR</span>
                        </a>
                        <a href="#favoritos" className="nav-item" onClick={() => onNavigate('favoritos')}>
                            FAVORITOS ({favorites.length})
                        </a>
                        {token && (
                            <button className="nav-item nav-logout-btn" onClick={onLogout}>
                                CERRAR SESIÓN
                            </button>
                        )}
                    </nav>
                </div>
            </header>

            {/* Toast flotante */}
            {toastMsg && (
                <div className="booksaw-toast">
                    <span>{toastMsg}</span>
                </div>
            )}

            {/* Cuerpo de Búsqueda */}
            <main className="search-main-section">
                <div className="search-container">

                    <div className="search-header-box">
                        <h1 className="search-main-headline">Búsqueda en Catálogo</h1>
                        <p className="search-lead-desc">
                            Encontrá obras de nuestra colección ingresando título, nombre de autor o número de ISBN.
                        </p>

                        {/* Barra de Búsqueda */}
                        <div className="search-input-wrapper">
                            <svg className="search-input-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#8C827A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="11" cy="11" r="8"></circle>
                                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                            </svg>
                            <input
                                id="search-input-field"
                                type="text"
                                className="search-input-field"
                                placeholder="Buscar por título, autor o ISBN..."
                                value={searchTerm}
                                onChange={handleInputChange}
                                maxLength={100}
                                autoFocus
                            />
                            {searchTerm && (
                                <button
                                    type="button"
                                    className="search-clear-btn"
                                    onClick={handleClearSearch}
                                    title="Limpiar búsqueda"
                                    aria-label="Limpiar búsqueda"
                                >
                                    ✕
                                </button>
                            )}
                        </div>

                        {/* Contador de resultados */}
                        <div className="search-status-bar">
                            {searchTerm.trim() ? (
                                <span>
                                    Resultados para <strong>"{searchTerm}"</strong>: {books.length} {books.length === 1 ? 'libro encontrado' : 'libros encontrados'}
                                </span>
                            ) : (
                                <span>Explorando catálogo editorial ({books.length} títulos disponibles)</span>
                            )}
                        </div>
                    </div>

                    {/* Estado de carga */}
                    {loading && (
                        <div className="search-loading-state">
                            <div className="editorial-spinner"></div>
                            <p>Buscando en los registros editoriales...</p>
                        </div>
                    )}

                    {/* Sin resultados */}
                    {!loading && books.length === 0 && (
                        <div className="search-empty-box">
                            <h3 className="empty-title">Sin coincidencias</h3>
                            <p className="empty-desc">
                                No encontramos libros que coincidan con <strong>"{searchTerm}"</strong>. Probá verificando la ortografía, buscando solo el apellido del autor o limpiando el filtro.
                            </p>
                            <button className="editorial-action-btn" onClick={handleClearSearch}>
                                VER TODO EL CATÁLOGO
                            </button>
                        </div>
                    )}

                    {/* Grilla de libros encontrados */}
                    {!loading && books.length > 0 && (
                        <div className="editorial-books-grid">
                            {books.map((book) => {
                                const fav = isFavorite(book.id);
                                return (
                                    <article key={book.id} className="book-card-item">
                                        <div className="book-3d-cover">
                                            <div className="book-3d-spine"></div>
                                            <div className="book-3d-body">
                                                <div className="book-3d-border">
                                                    <span className="book-3d-tag">{book.nombreLibreria || 'EDICIÓN ESPECIAL'}</span>
                                                    <h4 className="book-3d-title">{book.titulo}</h4>
                                                    <p className="book-3d-author">{book.autor}</p>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="book-card-caption">
                                            <div className="caption-text">
                                                <h3 className="caption-title">{book.titulo}</h3>
                                                <p className="caption-author">{book.autor}</p>
                                            </div>
                                            <button
                                                className="caption-heart-btn"
                                                onClick={() => handleToggleFavorite(book)}
                                                title={fav ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                                                aria-label={fav ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                                            >
                                                <svg viewBox="0 0 24 24" width="19" height="19" fill={fav ? "#A68658" : "none"} stroke={fav ? "#A68658" : "#8C827A"} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                                    <path d="M12 6.8 C10.8 4.2 7.8 2.6 4.8 3.6 C1.8 4.6 1.2 8.8 3 12.2 C5 16 11.2 20.8 12 21.4 C12.8 20.8 19 16 21 12.2 C22.8 8.8 22.2 4.6 19.2 3.6 C16.2 2.6 13.2 4.2 12 6.8 Z" />
                                                </svg>
                                            </button>
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    )}

                </div>
            </main>

            {/* Footer */}
            <footer className="booksaw-footer">
                <div className="footer-container">
                    <div className="footer-links">
                        <a href="#ayuda">AYUDA</a>
                        <span className="dot">•</span>
                        <a href="#envios">ENVÍOS</a>
                        <span className="dot">•</span>
                        <a href="#terminos">TÉRMINOS</a>
                        <span className="dot">•</span>
                        <a href="#privacidad">PRIVACIDAD</a>
                    </div>
                    <p className="footer-copy">
                        © 2026 LOS LIBROS DE BUYSAN — TODOS LOS DERECHOS RESERVADOS
                    </p>
                </div>
            </footer>
        </div>
    );
}
