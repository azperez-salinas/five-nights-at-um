import React, { useState, useEffect } from 'react';
import './Favorites.css';

export default function Favorites({ token, onLogout, onNavigate }) {
    const [favorites, setFavorites] = useState([]);
    const [catalogBooks, setCatalogBooks] = useState([]);
    const [activeTab, setActiveTab] = useState('mis-favoritos');
    const [loading, setLoading] = useState(true);
    const [toastMsg, setToastMsg] = useState('');

    const showToast = (msg) => {
        setToastMsg(msg);
        setTimeout(() => setToastMsg(''), 3000);
    };

    const fetchFavorites = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/favorites', {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
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

    const fetchCatalog = async () => {
        try {
            const res = await fetch('http://localhost:8080/api/books?page=1');
            if (res.ok) {
                const data = await res.json();
                setCatalogBooks(data.items || []);
            }
        } catch (err) {
            console.error(err);
        }
    };

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            await Promise.all([fetchFavorites(), fetchCatalog()]);
            setLoading(false);
        };
        init();
    }, [token]);

    const handleToggleFavorite = async (book) => {
        const isFav = favorites.some((f) => f.id === book.id);

        if (isFav) {
            try {
                const res = await fetch(`http://localhost:8080/api/favorites/${book.id}`, {
                    method: 'DELETE',
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                });
                if (res.ok || res.status === 204) {
                    setFavorites((prev) => prev.filter((f) => f.id !== book.id));
                    showToast(`"${book.titulo}" quitado de favoritos`);
                } else if (res.status === 401) {
                    onLogout();
                }
            } catch (err) {
                showToast('Error al quitar de favoritos');
            }
        } else {
            try {
                const res = await fetch(`http://localhost:8080/api/favorites/${book.id}`, {
                    method: 'POST',
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                });
                if (res.ok || res.status === 201) {
                    setFavorites((prev) => [...prev, book]);
                    showToast(`"${book.titulo}" agregado a favoritos`);
                } else if (res.status === 401) {
                    onLogout();
                }
            } catch (err) {
                showToast('Error al agregar a favoritos');
            }
        }
    };

    const isFavorite = (bookId) => favorites.some((f) => f.id === bookId);

    return (
        <div className="booksaw-page">
            <header className="booksaw-nav">
                <div className="booksaw-nav-container">
                    <div className="booksaw-logo" onClick={() => onNavigate('inicio')} style={{ cursor: 'pointer' }}>
                        <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                        <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                    </div>

                    <nav className="booksaw-menu">
                        <a href="#inicio" className="nav-item" onClick={() => onNavigate('inicio')}>INICIO</a>
                        <a href="#novedades" className="nav-item">NOVEDADES</a>
                        <a href="#temas" className="nav-item">TEMAS</a>
                        <a href="#colecciones" className="nav-item">COLECCIONES</a>
                        <a href="#favoritos" className="nav-item active">FAVORITOS ({favorites.length})</a>
                        <button className="nav-item nav-logout-btn" onClick={onLogout}>                            CERRAR SESIÓN
                        </button>
                    </nav>
                </div>
            </header>

            {toastMsg && (
                <div className="booksaw-toast">
                    <span>{toastMsg}</span>
                </div>
            )}

            <main className="favorites-editorial-main">
                <div className="favorites-editorial-wrapper">

                    <div className="favorites-header-section">
                        <h1 className="favorites-main-headline">Mis Libros Favoritos</h1>
                        <p className="favorites-lead-desc">
                            Tus obras seleccionadas y lecturas pendientes guardadas en tu cuenta.
                        </p>

                        <div className="editorial-tabs-row">
                            <button
                                className={`tab-link ${activeTab === 'mis-favoritos' ? 'active' : ''}`}
                                onClick={() => setActiveTab('mis-favoritos')}
                            >
                                GUARDADOS ({favorites.length})
                            </button>
                            <button
                                className={`tab-link ${activeTab === 'catalogo' ? 'active' : ''}`}
                                onClick={() => setActiveTab('catalogo')}
                            >
                                EXPLORAR CATÁLOGO
                            </button>
                        </div>
                    </div>

                    {loading && (
                        <div className="favorites-loading-state">
                            <div className="editorial-spinner"></div>
                            <p>Consultando catálogo editorial...</p>
                        </div>
                    )}

                    {!loading && activeTab === 'mis-favoritos' && (
                        <div>
                            {favorites.length === 0 ? (
                                <div className="favorites-empty-card">
                                    <h3 className="empty-title">Aún no tenés libros guardados</h3>
                                    <p className="empty-desc">
                                        Explorá nuestro catálogo y tocá el corazón para armar tu lista de lecturas preferidas.
                                    </p>
                                    <button
                                        className="editorial-action-btn"
                                        onClick={() => setActiveTab('catalogo')}
                                    >
                                        EXPLORAR CATÁLOGO
                                    </button>
                                </div>
                            ) : (
                                <div className="editorial-books-grid">
                                    {favorites.map((book) => (
                                        <article key={book.id} className="book-card-item">
                                            {/* Portada */}
                                            <div className="book-3d-cover">
                                                <div className="book-3d-spine"></div>
                                                <div className="book-3d-body">
                                                    <div className="book-3d-border">
                                                        <span className="book-3d-tag">{book.nombreLibreria || 'EDICIÓN ESPECIAL'}</span>
                                                        <h4 className="book-3d-title">{book.titulo}</h4>
                                                        <div className="book-3d-ornament">✦ ❦ ✦</div>
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
                                                    title="Quitar de favoritos"
                                                    aria-label="Quitar de favoritos"
                                                >
                                                    <svg viewBox="0 0 24 24" width="19" height="19" fill="#A68658" stroke="#A68658" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                                                        <path d="M12 6.8 C10.8 4.2 7.8 2.6 4.8 3.6 C1.8 4.6 1.2 8.8 3 12.2 C5 16 11.2 20.8 12 21.4 C12.8 20.8 19 16 21 12.2 C22.8 8.8 22.2 4.6 19.2 3.6 C16.2 2.6 13.2 4.2 12 6.8 Z" />
                                                    </svg>

                                                </button>
                                            </div>

                                        </article>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}

                    {/* Pestaña B: Explorar Catálogo para Agregar */}
                    {activeTab === 'catalogo' && (
                        <div className="editorial-books-grid">
                            {catalogBooks.map((book) => {
                                const fav = isFavorite(book.id);
                                return (
                                    <article key={book.id} className="book-card-item">
                                        <div className="book-3d-cover">
                                            <div className="book-3d-spine"></div>
                                            <div className="book-3d-body">
                                                <div className="book-3d-border">
                                                    <span className="book-3d-tag">{book.nombreLibreria || 'EDICIÓN ESPECIAL'}</span>
                                                    <h4 className="book-3d-title">{book.titulo}</h4>
                                                    <div className="book-3d-ornament">✦ ❦ ✦</div>
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
