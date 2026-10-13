import { useState } from 'react';
import Nav from './Nav';
import './Register.css';
import './AdminPanel.css';

const OWNERS_URL = 'http://localhost:8080/api/admin/owners';
const PASSWORD_URL = 'http://localhost:8080/api/admin/password';

const EMPTY_OWNER = { username: '', email: '', nombreLibreria: '' };

// R18: the admin panel only allows two actions, creating owners and changing the admin's own password
export default function AdminPanel({ session, onUnauthorized, onLogout }) {
    return (
        <div className="booksaw-page">
            <Nav active="admin" session={session} onLogout={onLogout} />

            <main className="booksaw-main">
                <div className="admin-panel">
                    <CreateOwnerCard token={session.token} onUnauthorized={onUnauthorized} />
                    <ChangeAdminPasswordCard token={session.token} onUnauthorized={onUnauthorized} />
                </div>
            </main>
        </div>
    );
}

function CreateOwnerCard({ token, onUnauthorized }) {
    const [form, setForm] = useState(EMPTY_OWNER);
    const [saving, setSaving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    // The temporary password lives only in this state: it is shown once and dropped on "Listo"
    const [created, setCreated] = useState(null);
    const [copied, setCopied] = useState(false);

    const updateField = (field) => (e) => setForm({ ...form, [field]: e.target.value });

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        const owner = {
            username: form.username.trim(),
            email: form.email.trim(),
            nombreLibreria: form.nombreLibreria.trim(),
        };
        if (!owner.username || !owner.email || !owner.nombreLibreria) {
            setErrorMsg('Por favor, completá todos los campos.');
            return;
        }
        if (!/^[A-Za-z0-9_]{3,30}$/.test(owner.username)) {
            setErrorMsg('El usuario debe tener entre 3 y 30 caracteres (letras, números o guión bajo).');
            return;
        }

        setSaving(true);
        try {
            const response = await fetch(OWNERS_URL, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                // Only the allowed fields: the backend always assigns the DUENO role
                body: JSON.stringify(owner),
            });

            if (response.status === 201) {
                const data = await response.json();
                setCreated(data);
                setCopied(false);
                setForm(EMPTY_OWNER);
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 409) {
                setErrorMsg('El usuario o el email ya están registrados.');
            } else if (response.status === 400) {
                setErrorMsg('Revisá los datos: el email debe ser válido y el nombre de la librería tener al menos 2 caracteres.');
            } else {
                setErrorMsg('Ocurrió un error al crear el dueño. Intentalo de nuevo.');
            }
        } catch {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setSaving(false);
        }
    };

    const copyPassword = async () => {
        try {
            await navigator.clipboard.writeText(created.passwordTemporal);
            setCopied(true);
        } catch {
            setCopied(false);
        }
    };

    return (
        <section className="register-card">
            <div className="card-header">
                <span className="card-badge">ADMINISTRADOR</span>
                <h2 className="card-title">Crear dueño</h2>
                <p className="card-subtitle">
                    Se genera una contraseña temporal que el dueño deberá cambiar en su primer inicio de sesión.
                </p>
            </div>

            {errorMsg && (
                <div className="alert-box alert-error" role="alert">
                    <span>{errorMsg}</span>
                </div>
            )}

            {created ? (
                <div className="temp-password-result" role="status">
                    <p>
                        Se creó la cuenta <strong>{created.username}</strong> para la librería{' '}
                        <strong>{created.nombreLibreria}</strong>.
                    </p>
                    <p className="temp-password-label">Contraseña temporal</p>
                    <div className="temp-password-box">
                        <code>{created.passwordTemporal}</code>
                        <button type="button" className="btn-toggle-pass" onClick={copyPassword}>
                            {copied ? 'Copiada' : 'Copiar'}
                        </button>
                    </div>
                    <p className="temp-password-warning">
                        Entregásela al dueño ahora: no se vuelve a mostrar y no se puede recuperar.
                    </p>
                    <button type="button" className="btn-submit-pill" onClick={() => setCreated(null)}>
                        LISTO
                    </button>
                </div>
            ) : (
                <form onSubmit={handleSubmit} className="register-form" noValidate>
                    <div className="input-group">
                        <label htmlFor="owner-username">Nombre de usuario</label>
                        <input
                            id="owner-username"
                            type="text"
                            placeholder="Ej. libreria_sur"
                            value={form.username}
                            onChange={updateField('username')}
                            autoComplete="off"
                        />
                    </div>

                    <div className="input-group">
                        <label htmlFor="owner-email">Correo electrónico</label>
                        <input
                            id="owner-email"
                            type="email"
                            placeholder="dueno@ejemplo.com"
                            value={form.email}
                            onChange={updateField('email')}
                            autoComplete="off"
                        />
                    </div>

                    <div className="input-group">
                        <label htmlFor="owner-library">Nombre de la librería</label>
                        <input
                            id="owner-library"
                            type="text"
                            placeholder="Ej. Librería del Sur"
                            value={form.nombreLibreria}
                            onChange={updateField('nombreLibreria')}
                            autoComplete="off"
                        />
                    </div>

                    <button type="submit" className="btn-submit-pill" disabled={saving}>
                        {saving ? 'CREANDO...' : 'CREAR DUEÑO'}
                    </button>
                </form>
            )}
        </section>
    );
}

function ChangeAdminPasswordCard({ token, onUnauthorized }) {
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [saving, setSaving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [successMsg, setSuccessMsg] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');
        setSuccessMsg('');

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

        setSaving(true);
        try {
            const response = await fetch(PASSWORD_URL, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({ currentPassword, newPassword }),
            });

            if (response.ok) {
                setCurrentPassword('');
                setNewPassword('');
                setConfirmPassword('');
                setSuccessMsg('Tu contraseña se actualizó correctamente.');
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 400) {
                // The backend does not say which field failed (RS31)
                setErrorMsg('No pudimos cambiar la contraseña. Verificá que la contraseña actual sea correcta.');
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
        <section className="register-card">
            <div className="card-header">
                <h2 className="card-title">Cambiar mi contraseña</h2>
                <p className="card-subtitle">Por seguridad, se exige la contraseña actual.</p>
            </div>

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

            <form onSubmit={handleSubmit} className="register-form" noValidate>
                <div className="input-group">
                    <label htmlFor="admin-current-password">Contraseña actual</label>
                    <input
                        id="admin-current-password"
                        type="password"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        autoComplete="current-password"
                    />
                </div>

                <div className="input-group">
                    <label htmlFor="admin-new-password">Nueva contraseña</label>
                    <input
                        id="admin-new-password"
                        type="password"
                        placeholder="Mínimo 8 caracteres"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        autoComplete="new-password"
                    />
                </div>

                <div className="input-group">
                    <label htmlFor="admin-confirm-password">Confirmar nueva contraseña</label>
                    <input
                        id="admin-confirm-password"
                        type="password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                    />
                </div>

                <button type="submit" className="btn-submit-pill" disabled={saving}>
                    {saving ? 'GUARDANDO...' : 'CAMBIAR CONTRASEÑA'}
                </button>
            </form>
        </section>
    );
}
