import { useEffect, useRef, useState } from 'react';

const API_URL = 'http://localhost:8080/api/users/libreria';

// R14: section at the bottom of MyLibrary to remove the owner's library (soft delete)
export default function OwnLibrary({ token, onUnauthorized, onRemoved }) {
    const [library, setLibrary] = useState(null);
    const [failed, setFailed] = useState(false);
    const [confirmText, setConfirmText] = useState('');
    const [removing, setRemoving] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const dialogRef = useRef(null);
    const cancelRef = useRef(null);

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
                // 404: no active library, MyLibrary already explains it
                if (response.status === 404) return null;
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((data) => data && setLibrary(data))
            .catch((err) => {
                if (err.name !== 'AbortError') setFailed(true);
            });

        return () => controller.abort();
    }, [token, onUnauthorized]);

    const openDialog = () => {
        setConfirmText('');
        setErrorMsg('');
        dialogRef.current.showModal();
        cancelRef.current.focus();
    };

    const handleRemove = async (e) => {
        e.preventDefault();
        setErrorMsg('');
        setRemoving(true);
        try {
            const response = await fetch(`${API_URL}/${library.id}`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${token}` },
            });

            if (response.status === 204 || response.status === 404) {
                // 404: it was already removed (e.g. from another tab), same end state
                dialogRef.current.close();
                onRemoved();
            } else if (response.status === 401) {
                onUnauthorized();
            } else {
                setErrorMsg('No pudimos dar de baja la librería. Intentalo de nuevo.');
            }
        } catch {
            setErrorMsg('No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.');
        } finally {
            setRemoving(false);
        }
    };

    if (failed) {
        return <p className="card-subtitle">No pudimos cargar los datos de tu librería.</p>;
    }
    if (!library) return null;

    const matches = confirmText.trim() === library.nombre;

    return (
        <section className="remove-library" aria-labelledby="remove-library-section">
            <div>
                <h2 id="remove-library-section" className="csv-import-title">Dar de baja la librería</h2>
                <p className="card-subtitle">Tu librería dejará de estar disponible en el sitio.</p>
            </div>
            <button type="button" className="btn-danger-outline" onClick={openDialog}>
                Dar de baja
            </button>

            <dialog ref={dialogRef} className="my-library-dialog" aria-labelledby="remove-library-title">
                <form onSubmit={handleRemove} className="register-form">
                    <h2 id="remove-library-title" className="card-title">¿Dar de baja "{library.nombre}"?</h2>
                    <p className="card-subtitle">
                        Tu librería y sus libros dejarán de mostrarse. Esta acción no se puede deshacer.
                    </p>

                    {errorMsg && (
                        <div className="alert-box alert-error" role="alert">
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    <div className="input-group">
                        <label htmlFor="confirm-library-name">
                            Para confirmar, escribí <strong>{library.nombre}</strong>
                        </label>
                        <input
                            id="confirm-library-name"
                            type="text"
                            autoComplete="off"
                            value={confirmText}
                            onChange={(e) => setConfirmText(e.target.value)}
                            disabled={removing}
                        />
                    </div>

                    <div className="my-library-dialog-actions">
                        <button
                            type="button"
                            ref={cancelRef}
                            className="btn-cancel"
                            onClick={() => dialogRef.current.close()}
                            disabled={removing}
                        >
                            Cancelar
                        </button>
                        <button type="submit" className="btn-danger" disabled={!matches || removing}>
                            {removing ? 'DANDO DE BAJA...' : 'DAR DE BAJA'}
                        </button>
                    </div>
                </form>
            </dialog>
        </section>
    );
}
