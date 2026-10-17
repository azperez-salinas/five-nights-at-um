import { useState } from 'react';
import Nav from './Nav';
import { useLanguage } from './language';
import './Register.css';
import './AdminPanel.css';

const OWNERS_URL = 'http://localhost:8080/api/admin/owners';

const EMPTY_OWNER = { username: '', email: '', nombreLibreria: '' };

// R18: el panel de administrador muestra únicamente la creación de dueños
export default function AdminPanel({ session, onUnauthorized, onLogout }) {
    return (
        <div className="booksaw-page">
            <Nav active="admin" session={session} onLogout={onLogout} />

            <main className="booksaw-main">
                <div className="admin-panel">
                    <CreateOwnerCard token={session.token} onUnauthorized={onUnauthorized} />
                </div>
            </main>
        </div>
    );
}

function CreateOwnerCard({ token, onUnauthorized }) {
    const { t } = useLanguage();
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
            setErrorMsg(t('admin_err_required'));
            return;
        }
        if (!/^[A-Za-z0-9_]{3,30}$/.test(owner.username)) {
            setErrorMsg(t('admin_err_username'));
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
                setErrorMsg(t('admin_err_conflict'));
            } else if (response.status === 400) {
                setErrorMsg(t('admin_err_invalid'));
            } else {
                setErrorMsg(t('admin_err_generic'));
            }
        } catch {
            setErrorMsg(t('admin_err_network'));
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
                <span className="card-badge">{t('admin_badge')}</span>
                <h2 className="card-title">{t('admin_create_owner_title')}</h2>
                <p className="card-subtitle">
                    {t('admin_create_owner_desc')}
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
                        {t('admin_created_account_for')} <strong>{created.username}</strong>{' '}
                        {t('admin_created_bookstore_for')} <strong>{created.nombreLibreria}</strong>.
                    </p>
                    <p className="temp-password-label">{t('admin_temp_pass_label')}</p>
                    <div className="temp-password-box">
                        <code>{created.passwordTemporal}</code>
                        <button type="button" className="btn-toggle-pass" onClick={copyPassword}>
                            {copied ? t('admin_copied') : t('admin_copy')}
                        </button>
                    </div>
                    <p className="temp-password-warning">
                        {t('admin_temp_pass_warning')}
                    </p>
                    <button type="button" className="btn-submit-pill" onClick={() => setCreated(null)}>
                        {t('admin_done_btn')}
                    </button>
                </div>
            ) : (
                <form onSubmit={handleSubmit} className="register-form" noValidate>
                    <div className="input-group">
                        <label htmlFor="owner-username">{t('admin_username')}</label>
                        <input
                            id="owner-username"
                            type="text"
                            placeholder={t('admin_username_placeholder')}
                            value={form.username}
                            onChange={updateField('username')}
                            autoComplete="off"
                        />
                    </div>

                    <div className="input-group">
                        <label htmlFor="owner-email">{t('admin_email')}</label>
                        <input
                            id="owner-email"
                            type="email"
                            placeholder={t('admin_email_placeholder')}
                            value={form.email}
                            onChange={updateField('email')}
                            autoComplete="off"
                        />
                    </div>

                    <div className="input-group">
                        <label htmlFor="owner-library">{t('admin_library_name')}</label>
                        <input
                            id="owner-library"
                            type="text"
                            placeholder={t('admin_library_placeholder')}
                            value={form.nombreLibreria}
                            onChange={updateField('nombreLibreria')}
                            autoComplete="off"
                        />
                    </div>

                    <button type="submit" className="btn-submit-pill" disabled={saving}>
                        {saving ? t('admin_creating_btn') : t('admin_create_btn')}
                    </button>
                </form>
            )}
        </section>
    );
}

