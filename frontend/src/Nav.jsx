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
                    <a href="#inicio" className={itemClass('inicio')}>INICIO</a>
                    <a href="#catalogo" className={itemClass('catalogo')}>CATÁLOGO</a>
                    {session && (
                        <a href="#perfil" className={itemClass('perfil')}>
                            {session.username.toUpperCase()}
                        </a>
                    )}
                </nav>
            </div>
        </header>
    );
}
