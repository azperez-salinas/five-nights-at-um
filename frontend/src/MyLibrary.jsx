import { useEffect, useRef, useState } from 'react';
import BookCover from './BookCover';
import Nav from './Nav';
import './Catalog.css';
import './MyLibrary.css';

const API_URL = 'http://localhost:8080/api/books';
const FIELDS = ['titulo', 'autor', 'isbn', 'portadaUrl'];
const CONNECTION_ERROR = 'No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.';
const GONE_MSG = 'Ese libro ya no está disponible.';

const isHttpUrl = (value) => {
    try {
        return ['http:', 'https:'].includes(new URL(value).protocol);
    } catch {
        return false;
    }
};

// R19: only the fields whose trimmed value differs from the book are sent (partial update)
const changedFields = (book, form) => Object.fromEntries(
    FIELDS.filter((f) => form[f].trim() !== (book[f] ?? '')).map((f) => [f, form[f].trim()])
);

const validate = (book, changes) => {
    if (['titulo', 'autor', 'isbn'].some((f) => changes[f] === '')) {
        return 'Título, autor e ISBN son obligatorios.';
    }
    // The backend has no way to clear the cover: an empty value is rejected
    if (changes.portadaUrl === '' && book.portadaUrl) {
        return 'La portada no se puede quitar, solo reemplazar por otra URL.';
    }
    if (changes.portadaUrl && !isHttpUrl(changes.portadaUrl)) {
        return 'La URL de la portada debe empezar con http:// o https://.';
    }
    return '';
};

// R19: DUENO only (App renders it just for that role)
export default function MyLibrary({ session, onUnauthorized, onLogout }) {
    const token = session.token;
    const [page, setPage] = useState(1);
    const [reloadKey, setReloadKey] = useState(0);
    const requestKey = `${page}:${reloadKey}`;
    // { key, result } so a response is only shown for the request that produced it
    const [data, setData] = useState(null);
    // Request key that failed; 'none' marks a 404 (no active library)
    const [failure, setFailure] = useState(null);
    const [errorMsg, setErrorMsg] = useState('');
    const [successMsg, setSuccessMsg] = useState('');

    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(null);
    const [formError, setFormError] = useState('');
    const [saving, setSaving] = useState(false);
    const editRef = useRef(null);
    const firstFieldRef = useRef(null);

    const [deleting, setDeleting] = useState(null);
    const [removing, setRemoving] = useState(false);
    const deleteRef = useRef(null);
    const cancelDeleteRef = useRef(null);

    useEffect(() => {
        const controller = new AbortController();

        fetch(`${API_URL}/mine?page=${page}`, {
            headers: { Authorization: `Bearer ${token}` },
            signal: controller.signal,
        })
            .then((response) => {
                if (response.status === 401) {
                    onUnauthorized();
                    return null;
                }
                // 404: the owner has no enabled library
                if (response.status === 404) {
                    setFailure({ key: requestKey, none: true });
                    return null;
                }
                if (!response.ok) throw new Error();
                return response.json();
            })
            .then((result) => result && setData({ key: requestKey, result }))
            .catch((err) => {
                if (err.name !== 'AbortError') setFailure({ key: requestKey, none: false });
            });

        return () => controller.abort();
    }, [token, page, requestKey, onUnauthorized]);

    useEffect(() => {
        if (!editing) return;
        editRef.current.showModal();
        firstFieldRef.current.focus();
    }, [editing]);

    useEffect(() => {
        if (!deleting) return;
        deleteRef.current.showModal();
        cancelDeleteRef.current.focus();
    }, [deleting]);

    const failed = failure?.key === requestKey;
    const loading = data?.key !== requestKey && !failed;
    const books = data?.result;
    const totalPages = books?.totalPages ?? 1;

    const updateItems = (fn) => setData((prev) => ({
        ...prev,
        result: { ...prev.result, items: fn(prev.result.items) },
    }));

    const clearAlerts = () => {
        setErrorMsg('');
        setSuccessMsg('');
    };

    const removeGoneBook = (book) => {
        updateItems((items) => items.filter((b) => b.id !== book.id));
        setErrorMsg(GONE_MSG);
    };

    const openEdit = (book) => {
        clearAlerts();
        setFormError('');
        setForm(Object.fromEntries(FIELDS.map((f) => [f, book[f] ?? ''])));
        setEditing(book);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        const changes = changedFields(editing, form);
        const invalid = validate(editing, changes);
        if (invalid) {
            setFormError(invalid);
            return;
        }

        setFormError('');
        setSaving(true);
        try {
            const response = await fetch(`${API_URL}/${editing.id}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(changes),
            });

            if (response.ok) {
                const updated = await response.json();
                updateItems((items) => items.map((b) => (b.id === updated.id ? updated : b)));
                editRef.current.close();
                setSuccessMsg('Libro actualizado.');
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 404) {
                editRef.current.close();
                removeGoneBook(editing);
            } else if (response.status === 400) {
                // The backend does not say which field failed (RS31)
                const body = await response.json().catch(() => null);
                setFormError(body?.message || 'Revisá los datos: el ISBN tiene que ser válido y ningún campo puede ser demasiado largo.');
            } else {
                setFormError('Ocurrió un error al guardar los cambios. Intentalo de nuevo.');
            }
        } catch {
            setFormError(CONNECTION_ERROR);
        } finally {
            setSaving(false);
        }
    };

    const openDelete = (book) => {
        clearAlerts();
        setDeleting(book);
    };

    const handleDelete = async () => {
        setRemoving(true);
        try {
            const response = await fetch(`${API_URL}/${deleting.id}`, {
                method: 'DELETE',
                headers: { Authorization: `Bearer ${token}` },
            });

            if (response.status === 204) {
                const lastOnPage = books.items.length === 1;
                updateItems((items) => items.filter((b) => b.id !== deleting.id));
                deleteRef.current.close();
                setSuccessMsg('Libro dado de baja.');
                // Refetch so the page stays filled; an emptied page goes back one
                if (lastOnPage && page > 1) setPage(page - 1);
                else setReloadKey((k) => k + 1);
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 404) {
                deleteRef.current.close();
                removeGoneBook(deleting);
            } else {
                deleteRef.current.close();
                setErrorMsg('No pudimos dar de baja el libro. Intentalo de nuevo.');
            }
        } catch {
            deleteRef.current.close();
            setErrorMsg(CONNECTION_ERROR);
        } finally {
            setRemoving(false);
        }
    };

    const unchanged = editing && Object.keys(changedFields(editing, form)).length === 0;

    return (
        <div className="booksaw-page">
            <Nav active="mi-libreria" session={session} onLogout={onLogout} />

            <main className="catalog-main">
                <h1 className="catalog-title">Mi librería</h1>

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

                {loading && !books && <p className="catalog-status">Cargando libros...</p>}

                {failed && failure.none && <p className="catalog-status">No tenés una librería activa.</p>}

                {failed && !failure.none && (
                    <div className="alert-box alert-error" role="alert">
                        <span>No pudimos cargar tu librería. Por favor, intentá nuevamente en unos momentos.</span>
                    </div>
                )}

                {!failed && !loading && books && books.items.length === 0 && (
                    <p className="catalog-status">Tu librería todavía no tiene libros.</p>
                )}

                {!failed && books && books.items.length > 0 && (
                    <ul className="my-library-list" aria-busy={loading}>
                        {books.items.map((book) => (
                            <li key={book.id} className="my-library-row">
                                <div className="my-library-cover">
                                    <BookCover book={book} />
                                </div>
                                <div className="my-library-info">
                                    <h3 className="caption-title">{book.titulo}</h3>
                                    <p className="caption-author">{book.autor}</p>
                                    <p className="my-library-isbn">ISBN {book.isbn}</p>
                                </div>
                                <div className="my-library-actions">
                                    <button
                                        type="button"
                                        className="btn-submit-pill"
                                        onClick={() => openEdit(book)}
                                        aria-label={`Editar "${book.titulo}"`}
                                    >
                                        Editar
                                    </button>
                                    <button
                                        type="button"
                                        className="btn-danger"
                                        onClick={() => openDelete(book)}
                                        aria-label={`Dar de baja "${book.titulo}"`}
                                    >
                                        Dar de baja
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}

                {!failed && books && totalPages > 1 && (
                    <nav className="catalog-pagination" aria-label="Paginación de mi librería">
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => setPage(page - 1)}
                            disabled={loading || page <= 1}
                        >
                            ANTERIOR
                        </button>
                        <span>Página {page} de {totalPages}</span>
                        <button
                            type="button"
                            className="btn-submit-pill"
                            onClick={() => setPage(page + 1)}
                            disabled={loading || page >= totalPages}
                        >
                            SIGUIENTE
                        </button>
                    </nav>
                )}

                {/* R14: future "Zona de peligro" (library removal) goes here, below the book list */}
            </main>

            <dialog
                ref={editRef}
                className="my-library-dialog"
                aria-labelledby="edit-book-title"
                onClose={() => setEditing(null)}
            >
                {editing && (
                    <form onSubmit={handleSave} className="register-form" noValidate>
                        <h2 id="edit-book-title" className="card-title">Editar libro</h2>

                        {formError && (
                            <div className="alert-box alert-error" role="alert">
                                <span>{formError}</span>
                            </div>
                        )}

                        {[
                            ['titulo', 'Título', 'text'],
                            ['autor', 'Autor', 'text'],
                            ['isbn', 'ISBN', 'text'],
                            ['portadaUrl', 'URL de portada (opcional)', 'url'],
                        ].map(([field, label, type], i) => (
                            <div className="input-group" key={field}>
                                <label htmlFor={`edit-${field}`}>{label}</label>
                                <input
                                    id={`edit-${field}`}
                                    ref={i === 0 ? firstFieldRef : undefined}
                                    type={type}
                                    value={form[field]}
                                    onChange={(e) => setForm({ ...form, [field]: e.target.value })}
                                />
                            </div>
                        ))}

                        <button type="submit" className="btn-submit-pill" disabled={saving || unchanged}>
                            {saving ? 'GUARDANDO...' : 'GUARDAR CAMBIOS'}
                        </button>
                        <button type="button" className="btn-cancel" onClick={() => editRef.current.close()} disabled={saving}>
                            Cancelar
                        </button>
                    </form>
                )}
            </dialog>

            <dialog
                ref={deleteRef}
                className="my-library-dialog"
                aria-labelledby="delete-book-title"
                onClose={() => setDeleting(null)}
            >
                {deleting && (
                    <div className="register-form">
                        <h2 id="delete-book-title" className="card-title">¿Dar de baja "{deleting.titulo}"?</h2>
                        <p className="card-subtitle">Va a dejar de verse en el catálogo, la búsqueda y el detalle.</p>
                        <div className="my-library-dialog-actions">
                            <button
                                type="button"
                                ref={cancelDeleteRef}
                                className="btn-cancel"
                                onClick={() => deleteRef.current.close()}
                                disabled={removing}
                            >
                                Cancelar
                            </button>
                            <button type="button" className="btn-danger" onClick={handleDelete} disabled={removing}>
                                {removing ? 'DANDO DE BAJA...' : 'DAR DE BAJA'}
                            </button>
                        </div>
                    </div>
                )}
            </dialog>
        </div>
    );
}
