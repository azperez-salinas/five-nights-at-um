import { useEffect, useState } from 'react';
import Nav from './Nav';
import './Register.css';

const API_URL = 'http://localhost:8080/api/users/me';

export default function Profile({ session, onUsernameChanged, onUnauthorized }) {
    const [profile, setProfile] = useState(null);
    const [loadFailed, setLoadFailed] = useState(false);
    const [editing, setEditing] = useState(false);

    const [username, setUsername] = useState('');
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [saving, setSaving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [successMsg, setSuccessMsg] = useState('');

    const token = session?.token;

    useEffect(() => {
        if (!token) return;
        const controller = new AbortController();

        fetch(API_URL, {
            headers: { Authorization: `Bearer ${token}` },
            signal: controller.signal,
        })
            .then((response) => {
                if (response.status === 401) {
                    onUnauthorized();
                    return null;
                }
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((data) => data && setProfile(data))
            .catch((err) => {
                if (err.name !== 'AbortError') setLoadFailed(true);
            });

        return () => controller.abort();
    }, [token, onUnauthorized]);

    const clearPasswords = () => {
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
    };

    const startEditing = () => {
        setUsername(profile.username);
        clearPasswords();
        setErrorMsg('');
        setSuccessMsg('');
        setEditing(true);
    };

    const cancelEditing = () => {
        clearPasswords();
        setErrorMsg('');
        setEditing(false);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');
        setSuccessMsg('');

        const trimmedUsername = username.trim();
        const usernameChanged = trimmedUsername.toLowerCase() !== profile.username;
        const passwordChanged = newPassword.length > 0;

        if (!usernameChanged && !passwordChanged) {
            setErrorMsg('No hay cambios para guardar.');
            return;
        }

        if (usernameChanged && !/^[A-Za-z0-9_]{3,30}$/.test(trimmedUsername)) {
            setErrorMsg('El usuario debe tener entre 3 y 30 caracteres (solo letras, números o guión bajo).');
            return;
        }

        if (passwordChanged) {
            if (!currentPassword) {
                setErrorMsg('Para cambiar la contraseña tenés que ingresar la contraseña actual.');
                return;
            }
            if (newPassword.length < 8) {
                setErrorMsg('La nueva contraseña debe tener al menos 8 caracteres.');
                return;
            }
            if (newPassword !== confirmPassword) {
                setErrorMsg('Las contraseñas nuevas no coinciden.');
                return;
            }
        }

        // Only the fields being changed are sent
        const body = {};
        if (usernameChanged) body.username = trimmedUsername;
        if (passwordChanged) {
            body.currentPassword = currentPassword;
            body.newPassword = newPassword;
        }

        setSaving(true);
        try {
            const response = await fetch(API_URL, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(body),
            });

            if (response.ok) {
                const updated = await response.json();
                setProfile(updated);
                onUsernameChanged(updated.username);
                clearPasswords();
                setEditing(false);
                setSuccessMsg('Tus datos se actualizaron correctamente.');
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 409) {
                setErrorMsg('Ese nombre de usuario ya está en uso.');
            } else if (response.status === 400) {
                // The backend does not say which field failed (RS31)
                setErrorMsg('No pudimos guardar los cambios. Revisá los datos y, si cambiaste la contraseña, que la actual sea correcta.');
            } else {
                setErrorMsg('Ocurrió un error al guardar los cambios. Intentalo de nuevo.');
            }
        } catch {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="booksaw-page">
            <Nav active="perfil" session={session} />

            <main className="booksaw-main">
                <div className="register-card">
                    <div className="card-header">
                        <h2 className="card-title">Mi perfil</h2>
                    </div>

                    {!session && (
                        <p className="card-subtitle">
                            Tenés que iniciar sesión para ver tu perfil. <a href="#inicio" className="login-link">Crear cuenta</a>
                        </p>
                    )}

                    {session && loadFailed && (
                        <div className="alert-box alert-error" role="alert">
                            <span>No pudimos cargar tu perfil. Por favor, intentá nuevamente en unos momentos.</span>
                        </div>
                    )}

                    {session && !profile && !loadFailed && <p className="card-subtitle">Cargando perfil...</p>}

                    {errorMsg && (
                        <div className="alert-box alert-error" role="alert">
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    {successMsg && (
                        <div className="alert-box alert-success" role="alert">
                            <span className="alert-icon">✓</span>
                            <span>{successMsg}</span>
                        </div>
                    )}

                    {profile && !editing && (
                        <div className="register-form">
                            <p><strong>Usuario:</strong> {profile.username}</p>
                            <p><strong>Correo electrónico:</strong> {profile.email}</p>
                            <button type="button" className="btn-submit-pill" onClick={startEditing}>
                                EDITAR PERFIL
                            </button>
                        </div>
                    )}

                    {profile && editing && (
                        <form onSubmit={handleSubmit} className="register-form" noValidate>
                            <div className="input-group">
                                <label htmlFor="username">Nombre de usuario</label>
                                <input
                                    id="username"
                                    type="text"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    autoComplete="username"
                                />
                            </div>

                            <p className="card-subtitle">Completá estos campos solo si querés cambiar la contraseña.</p>

                            <div className="input-group">
                                <label htmlFor="currentPassword">Contraseña actual</label>
                                <input
                                    id="currentPassword"
                                    type="password"
                                    value={currentPassword}
                                    onChange={(e) => setCurrentPassword(e.target.value)}
                                    autoComplete="current-password"
                                />
                            </div>

                            <div className="input-group">
                                <label htmlFor="newPassword">Nueva contraseña</label>
                                <input
                                    id="newPassword"
                                    type="password"
                                    placeholder="Mínimo 8 caracteres"
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    autoComplete="new-password"
                                />
                            </div>

                            <div className="input-group">
                                <label htmlFor="confirmPassword">Confirmar nueva contraseña</label>
                                <input
                                    id="confirmPassword"
                                    type="password"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    autoComplete="new-password"
                                />
                            </div>

                            <button type="submit" className="btn-submit-pill" disabled={saving}>
                                {saving ? 'GUARDANDO...' : 'GUARDAR CAMBIOS'}
                            </button>
                            <button type="button" className="btn-cancel" onClick={cancelEditing} disabled={saving}>
                                Cancelar
                            </button>
                        </form>
                    )}
                </div>
            </main>
        </div>
    );
}
