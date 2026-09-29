import { useState } from 'react';
import './Register.css';

export default function Login({ onLoginSuccess, onNavigateToRegister }) {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!username.trim() || !password) {
            setErrorMsg('Por favor, completá usuario y contraseña.');
            return;
        }

        setLoading(true);

        try {
            const response = await fetch('http://localhost:8080/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: username.trim(),
                    password: password,
                }),
            });

            if (response.ok) {
                const data = await response.json();
                onLoginSuccess(data.token, data.username, data.role);
            } else if (response.status === 401 || response.status === 400) {
                setErrorMsg('Nombre de usuario o contraseña incorrectos.');
            } else {
                setErrorMsg('Ocurrió un error al iniciar sesión. Intentalo de nuevo.');
            }
        } catch {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setLoading(false);
        }
    };

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
                        <a href="#novedades" className="nav-item">NOVEDADES</a>
                        <a href="#temas" className="nav-item">TEMAS</a>
                        <a href="#colecciones" className="nav-item">COLECCIONES</a>
                        <a href="#nosotros" className="nav-item">SOBRE NOSOTROS</a>
                    </nav>
                </div>
            </header>

            <main className="booksaw-main">
                <div className="booksaw-content-wrapper">

                    <section className="booksaw-hero-col">
                        <div className="book-card-featured">
                            <div className="book-cover-3d">
                                <div className="book-spine"></div>
                                <div className="book-front">
                                    <div className="book-gold-border">
                                        <span className="book-tag">EDICIÓN ESPECIAL</span>
                                        <h3 className="book-front-title">LOS LIBROS<br />DE BUYSAN</h3>
                                        <div className="book-ornament">✦ ❦ ✦</div>
                                        <p className="book-front-sub">CATÁLOGO CURADO</p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="booksaw-hero-text">
                            <h1 className="hero-headline">Bienvenido de vuelta</h1>
                            <p className="hero-desc">
                                Ingresá a tu cuenta para acceder a tus libros favoritos, pedidos y novedades exclusivas.
                            </p>
                        </div>
                    </section>

                    <section className="booksaw-form-col">
                        <div className="register-card">
                            <div className="card-header">
                                <h2 className="card-title">Iniciar sesión</h2>
                                <p className="card-subtitle">Ingresá tus credenciales para acceder a tu cuenta de comprador.</p>
                            </div>

                            {errorMsg && (
                                <div className="alert-box alert-error" role="alert">
                                    <span>{errorMsg}</span>
                                </div>
                            )}

                            <form onSubmit={handleSubmit} className="register-form" noValidate>
                                <div className="input-group">
                                    <label htmlFor="login-username">Nombre de usuario</label>
                                    <input
                                        id="login-username"
                                        type="text"
                                        placeholder="Ej. JuanPerez123"
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        autoComplete="username"
                                        required
                                    />
                                </div>

                                <div className="input-group">
                                    <label htmlFor="login-password">Contraseña</label>
                                    <div className="password-input-wrapper">
                                        <input
                                            id="login-password"
                                            type={showPassword ? 'text' : 'password'}
                                            placeholder="Tu contraseña"
                                            value={password}
                                            onChange={(e) => setPassword(e.target.value)}
                                            autoComplete="current-password"
                                            required
                                        />
                                        <button
                                            type="button"
                                            className="btn-toggle-pass"
                                            onClick={() => setShowPassword(!showPassword)}
                                            aria-label={showPassword ? 'Ocultar contraseña' : 'Ver contraseña'}
                                        >
                                            {showPassword ? 'Ocultar' : 'Mostrar'}
                                        </button>
                                    </div>
                                </div>

                                <button
                                    type="submit"
                                    className="btn-submit-pill"
                                    disabled={loading}
                                >
                                    {loading ? 'INGRESANDO...' : 'INICIAR SESIÓN'}
                                </button>
                            </form>

                            <div className="card-footer">
                                <span>¿No tenés una cuenta? </span>
                                <a
                                    href="#registro"
                                    className="login-link"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        if (onNavigateToRegister) onNavigateToRegister();
                                    }}
                                >
                                    Registrate acá
                                </a>
                            </div>
                        </div>
                    </section>

                </div>
            </main>

            <footer className="booksaw-footer">
                <div className="footer-container">
                    <div className="footer-links">
                        <a href="#ayuda">AYUDA</a>
                        <span className="dot">•</span>
                        <a href="#contacto">CONTACTO</a>
                        <span className="dot">•</span>
                        <a href="#privacidad">POLÍTICA DE PRIVACIDAD</a>
                        <span className="dot">•</span>
                        <a href="#envios">ENVÍOS</a>
                    </div>
                    <div className="footer-copy">
                        © 2026 Los Libros de Buysan. Todos los derechos reservados.
                    </div>
                </div>
            </footer>
        </div>
    );
}
