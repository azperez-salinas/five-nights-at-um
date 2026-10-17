import './Register.css';
import { useTheme } from './theme';

function ThemeToggle() {
    const [theme, toggle] = useTheme();
    const label = theme === 'dark' ? 'Activar tema claro' : 'Activar tema oscuro';

    return (
        <button type="button" className="nav-item nav-theme-toggle" onClick={toggle} aria-label={label} title={label}>
            {theme === 'dark' ? (
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="4"></circle>
                    <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path>
                </svg>
            ) : (
                <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
                </svg>
            )}
        </button>
    );
}

export default function Nav({ active, session, onLogout }) {
    const itemClass = (name) => `nav-item${active === name ? ' active' : ''}`;

    const handleLogoutClick = (e) => {
        if (onLogout) {
            e.preventDefault();
            onLogout();
        }
    };

    // R18: the admin only has its panel, and an owner with a temporary password only the
    // password change screen, so the regular menu is hidden for both
    if (session && (session.role === 'ADMIN' || session.mustChangePassword)) {
        return (
            <header className="booksaw-nav">
                <div className="booksaw-nav-container">
                    <div className="booksaw-logo">
                        <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                        <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                    </div>
                    <nav className="booksaw-menu">
                        {session.role === 'ADMIN' && (
                            <>
                                <a href="#admin" className={itemClass('admin')}>PANEL</a>
                                <a href="#perfil" className={itemClass('perfil')}>
                                    {session.username.toUpperCase()}
                                </a>
                            </>
                        )}
                        <ThemeToggle />
                        <a href="#logout" className="nav-item" onClick={handleLogoutClick}>CERRAR SESIÓN</a>
                    </nav>
                </div>
            </header>
        );
    }

    return (
        <header className="booksaw-nav">
            <div className="booksaw-nav-container">
                <div className="booksaw-logo">
                    <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                    <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                </div>
                <nav className="booksaw-menu">
                    <a href="#catalogo" className={itemClass('catalogo')}>DESTACADOS</a>
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
                            {session.role === 'DUENO' && (
                                <a href="#mi-libreria" className={itemClass('mi-libreria')}>MI LIBRERÍA</a>
                            )}
                            <a href="#perfil" className={itemClass('perfil')}>
                                {session.username.toUpperCase()}
                            </a>
                            <ThemeToggle />
                            <a href="#logout" className="nav-item" onClick={handleLogoutClick}>CERRAR SESIÓN</a>
                        </>
                    ) : (
                        <>
                            <a href="#login" className={itemClass('login')}>INICIAR SESIÓN</a>
                            <a href="#registro" className={itemClass('registro')}>REGISTRARSE</a>
                        </>
                    )}
                </nav>
            </div>
        </header>
    );
}
