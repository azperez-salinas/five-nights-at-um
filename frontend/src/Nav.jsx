import './Register.css';

export default function Nav({ active, session }) {
    const itemClass = (name) => `nav-item${active === name ? ' active' : ''}`;

    return (
        <header className="booksaw-nav">
            <div className="booksaw-nav-container">
                <div className="booksaw-logo">
                    <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                    <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                </div>
                <nav className="booksaw-menu">
                    <a href="#catalogo" className={itemClass('catalogo')}>RECOMENDADOS</a>
                    <a href="#buscar" className={`${itemClass('buscar')} nav-search-item`}>
                        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="11" cy="11" r="7.5"></circle>
                            <line x1="21" y1="21" x2="16.5" y2="16.5"></line>
                        </svg>
                        <span>BUSCAR</span>
                    </a>

                    {session ? (
                        <>
                            <a href="#favoritos" className={itemClass('favoritos')}>FAVORITOS</a>
                            <a href="#perfil" className={itemClass('perfil')}>
                                {session.username.toUpperCase()}
                            </a>
                            <a href="#logout" className="nav-item">CERRAR SESIÓN</a>
                        </>
                    ) : (
                        <a href="#login" className="nav-item">INICIAR SESIÓN</a>
                    )}
                </nav>
            </div>
        </header>
    );
}
