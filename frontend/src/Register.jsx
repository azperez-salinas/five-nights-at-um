import { useState } from 'react';
import './Register.css';

export default function Register() {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [successMsg, setSuccessMsg] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');
        setSuccessMsg('');

        if (!username.trim() || !email.trim() || !password || !confirmPassword) {
            setErrorMsg('Por favor, completá todos los campos.');
            return;
        }

        const usernameRegex = /^[A-Za-z0-9_]{3,30}$/;
        if (!usernameRegex.test(username.trim())) {
            setErrorMsg('El usuario debe tener entre 3 y 30 caracteres (solo letras, números o guión bajo).');
            return;
        }

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email.trim())) {
            setErrorMsg('Ingresá un correo electrónico válido.');
            return;
        }

        if (password.length < 8) {
            setErrorMsg('La contraseña debe tener al menos 8 caracteres.');
            return;
        }

        if (password !== confirmPassword) {
            setErrorMsg('Las contraseñas no coinciden.');
            return;
        }

        setLoading(true);

        try {
            const response = await fetch('http://localhost:8080/api/auth/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    username: username.trim(),
                    email: email.trim(),
                    password: password,
                }),
            });

            if (response.ok) {
                setSuccessMsg('¡Cuenta creada con éxito! Ya podés iniciar sesión.');
                setUsername('');
                setEmail('');
                setPassword('');
                setConfirmPassword('');
            } else if (response.status === 409) {
                setErrorMsg('El nombre de usuario o correo ya se encuentra registrado.');
            } else if (response.status === 400) {
                setErrorMsg('Los datos ingresados no son válidos. Por favor, revisalos.');
            } else {
                setErrorMsg('Ocurrió un error al procesar el registro. Intentalo de nuevo.');
            }
        } catch (err) {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="booksaw-page">
            {/* Barra superior de navegación estilo BookSaw */}
            <header className="booksaw-nav">
                <div className="booksaw-nav-container">
                    <div className="booksaw-logo">
                        <span className="logo-title">LOS LIBROS DE BUYSAN</span>
                        <span className="logo-tagline">LIBRERÍA & EDITORIAL</span>
                    </div>
                    <nav className="booksaw-menu">
                        <a href="#inicio" className="nav-item active">INICIO</a>
                        <a href="#novedades" className="nav-item">NOVEDADES</a>
                        <a href="#temas" className="nav-item">TEMAS</a>
                        <a href="#colecciones" className="nav-item">COLECCIONES</a>
                        <a href="#nosotros" className="nav-item">SOBRE NOSOTROS</a>
                    </nav>
                </div>
            </header>

            {/* Contenido principal en 2 columnas */}
            <main className="booksaw-main">
                <div className="booksaw-content-wrapper">

                    {/* Columna Izquierda: Showcase Editorial */}
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
                            <h1 className="hero-headline">Descubrí tu próxima lectura</h1>
                            <p className="hero-desc">
                                Sumergite en nuestro catálogo de clásicos, títulos contemporáneos y rarezas literarias seleccionadas especialmente para vos.
                            </p>
                            <a href="#catalogo" className="hero-cta-link">
                                EXPLORÁ EL CATÁLOGO <span className="cta-arrow">→</span>
                            </a>
                        </div>
                    </section>

                    {/* Columna Derecha: Tarjeta de Registro */}
                    <section className="booksaw-form-col">
                        <div className="register-card">
                            <div className="card-header">
                                <h2 className="card-title">Crear cuenta</h2>
                                <p className="card-subtitle">Registrate para acceder a compras exclusivas, envíos rápidos y listas de lectura personalizadas.</p>
                            </div>

                            {errorMsg && (
                                <div className="alert-box alert-error" role="alert">
                                    <span className="alert-icon"></span>
                                    <span>{errorMsg}</span>
                                </div>
                            )}

                            {successMsg && (
                                <div className="alert-box alert-success" role="alert">
                                    <span className="alert-icon">✓</span>
                                    <span>{successMsg}</span>
                                </div>
                            )}

                            <form onSubmit={handleSubmit} className="register-form" noValidate>
                                <div className="input-group">
                                    <label htmlFor="username">Nombre de usuario</label>
                                    <input
                                        id="username"
                                        type="text"
                                        placeholder="Ej. belen_lector"
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        autoComplete="username"
                                        required
                                    />
                                </div>

                                <div className="input-group">
                                    <label htmlFor="email">Correo electrónico</label>
                                    <input
                                        id="email"
                                        type="email"
                                        placeholder="nombre@ejemplo.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        autoComplete="email"
                                        required
                                    />
                                </div>

                                <div className="input-group">
                                    <label htmlFor="password">Contraseña</label>
                                    <div className="password-input-wrapper">
                                        <input
                                            id="password"
                                            type={showPassword ? 'text' : 'password'}
                                            placeholder="Mínimo 8 caracteres"
                                            value={password}
                                            onChange={(e) => setPassword(e.target.value)}
                                            autoComplete="new-password"
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

                                <div className="input-group">
                                    <label htmlFor="confirmPassword">Confirmar contraseña</label>
                                    <input
                                        id="confirmPassword"
                                        type={showPassword ? 'text' : 'password'}
                                        placeholder="Repetí tu contraseña"
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        autoComplete="new-password"
                                        required
                                    />
                                </div>

                                <div className="terms-checkbox">
                                    <input type="checkbox" id="terms" required defaultChecked />
                                    <label htmlFor="terms">
                                        Acepto los <a href="#terminos">Términos y Condiciones</a> y la <a href="#privacidad">Política de Privacidad</a>.
                                    </label>
                                </div>

                                <button
                                    type="submit"
                                    className="btn-submit-pill"
                                    disabled={loading}
                                >
                                    {loading ? 'REGISTRANDO...' : 'CREAR CUENTA DE COMPRADOR'}
                                </button>
                            </form>

                            <div className="card-footer">
                                <span>¿Ya tenés una cuenta? </span>
                                <a href="#login" className="login-link">Iniciar sesión</a>
                            </div>
                        </div>
                    </section>

                </div>
            </main>

            {/* Pie de página */}
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
