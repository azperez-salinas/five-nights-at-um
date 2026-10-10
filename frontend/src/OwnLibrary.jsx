import { useEffect, useState } from 'react';

const API_URL = 'http://localhost:8080/api/users/libreria';

// R14: shown inside the profile for DUENO accounts only
export default function OwnLibrary({ token, onUnauthorized }) {
    const [library, setLibrary] = useState(null);
    // 'loading' | 'ready' | 'none' | 'error'
    const [status, setStatus] = useState('loading');
    const [removing, setRemoving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [successMsg, setSuccessMsg] = useState('');

    useEffect(() => {
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
                // 404: the owner has no enabled library (already removed)
                if (response.status === 404) {
                    setStatus('none');
                    return null;
                }
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((data) => {
                if (data) {
                    setLibrary(data);
                    setStatus('ready');
                }
            })
            .catch((err) => {
                if (err.name !== 'AbortError') setStatus('error');
            });

        return () => controller.abort();
    }, [token, onUnauthorized]);

    const handleRemove = async () => {
        const confirmed = window.confirm(
            `¿Seguro que querés quitar "${library.nombre}" del sistema? Sus libros dejarán de verse en el catálogo.`
        );
        if (!confirmed) return;

        setErrorMsg('');
        setRemoving(true);
        try {
            const response = await fetch(`${API_URL}/${library.id}`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${token}` },
            });

            if (response.status === 204) {
                setLibrary(null);
                setStatus('none');
                setSuccessMsg('Tu librería se quitó del sistema.');
            } else if (response.status === 401) {
                onUnauthorized();
            } else {
                setErrorMsg('No pudimos quitar la librería. Intentalo de nuevo.');
            }
        } catch {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setRemoving(false);
        }
    };

    return (
        <div className="register-form own-library">
            <h3 className="card-title">Mi librería</h3>

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

            {status === 'loading' && <p className="card-subtitle">Cargando librería...</p>}
            {status === 'error' && <p className="card-subtitle">No pudimos cargar tu librería.</p>}
            {status === 'none' && !successMsg && <p className="card-subtitle">No tenés una librería activa.</p>}

            {status === 'ready' && library && (
                <>
                    <p><strong>Nombre:</strong> {library.nombre}</p>
                    <button type="button" className="btn-cancel" onClick={handleRemove} disabled={removing}>
                        {removing ? 'QUITANDO...' : 'QUITAR MI LIBRERÍA'}
                    </button>
                </>
            )}
        </div>
    );
}
