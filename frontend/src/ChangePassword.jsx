import { useState } from 'react';
import Nav from './Nav';
import './Register.css';

const API_URL = 'http://localhost:8080/api/users/me';

// R18: an owner created by the admin must replace the temporary password on first login.
// The backend enforces it too: until then the token only allows changing the password.
export default function ChangePassword({ session, onPasswordChanged, onUnauthorized, onLogout }) {
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [saving, setSaving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!currentPassword || !newPassword || !confirmPassword) {
            setErrorMsg('Por favor, completá todos los campos.');
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
        if (newPassword === currentPassword) {
            setErrorMsg('La nueva contraseña tiene que ser distinta de la temporal.');
            return;
        }

        setSaving(true);
        try {
            const response = await fetch(API_URL, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${session.token}`,
                },
                body: JSON.stringify({ currentPassword, newPassword }),
            });

            if (response.ok) {
                // The backend revoked this token: the owner logs in again with the new password
                onPasswordChanged();
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 400) {
                setErrorMsg('No pudimos cambiar la contraseña. Verificá que la contraseña temporal sea correcta.');
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
            <Nav active="cambiar-password" session={session} onLogout={onLogout} />

            <main className="booksaw-main">
                <div className="register-card">
                    <div className="card-header">
                        <span className="card-badge">PRIMER INICIO DE SESIÓN</span>
                        <h2 className="card-title">Elegí tu contraseña</h2>
                        <p className="card-subtitle">
                            Tu cuenta se creó con una contraseña temporal. Para usar tu librería tenés que
                            reemplazarla por una propia.
                        </p>
                    </div>

                    {errorMsg && (
                        <div className="alert-box alert-error" role="alert">
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="register-form" noValidate>
                        <div className="input-group">
                            <label htmlFor="temp-current-password">Contraseña temporal</label>
                            <input
                                id="temp-current-password"
                                type="password"
                                value={currentPassword}
                                onChange={(e) => setCurrentPassword(e.target.value)}
                                autoComplete="current-password"
                            />
                        </div>

                        <div className="input-group">
                            <label htmlFor="temp-new-password">Nueva contraseña</label>
                            <input
                                id="temp-new-password"
                                type="password"
                                placeholder="Mínimo 8 caracteres"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                autoComplete="new-password"
                            />
                        </div>

                        <div className="input-group">
                            <label htmlFor="temp-confirm-password">Confirmar nueva contraseña</label>
                            <input
                                id="temp-confirm-password"
                                type="password"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                autoComplete="new-password"
                            />
                        </div>

                        <button type="submit" className="btn-submit-pill" disabled={saving}>
                            {saving ? 'GUARDANDO...' : 'GUARDAR CONTRASEÑA'}
                        </button>
                    </form>
                </div>
            </main>
        </div>
    );
}
