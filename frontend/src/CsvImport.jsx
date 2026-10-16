import { useRef, useState } from 'react';

const IMPORT_URL = 'http://localhost:8080/api/books/import';
const CONNECTION_ERROR = 'No pudimos conectar con el servicio. Por favor, intentá nuevamente en unos momentos.';
// Same order and names the backend parser expects (BookCsvImportService)
const COLUMNS = ['titulo', 'autor', 'isbn', 'cantidad_paginas', 'descripcion', 'anio'];
// Spring's default multipart limit (1 MB) is stricter than the service's own 2 MB check
const MAX_BYTES = 1024 * 1024;
const MAX_VISIBLE_ERRORS = 20;
const TEMPLATE = `\uFEFF${COLUMNS.join(';')}\nEl libro de ejemplo;Autora Ejemplo;9780306406157;320;Una breve descripción del libro.;2020\n`;

const downloadTemplate = () => {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'plantilla-libros.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url));
};

// R17: bulk book upload for the owner's own library
export default function CsvImport({ token, onUnauthorized, onImported }) {
    const [file, setFile] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [result, setResult] = useState(null);
    const [error, setError] = useState('');
    const [dragging, setDragging] = useState(false);
    const inputRef = useRef(null);

    const pickFile = (picked) => {
        setFile(picked ?? null);
        setError('');
        setResult(null);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setDragging(false);
        if (!uploading) pickFile(e.dataTransfer.files[0]);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setResult(null);
        if (!file.name.toLowerCase().endsWith('.csv')) {
            setError('El archivo debe tener extensión .csv.');
            return;
        }
        if (file.size > MAX_BYTES) {
            setError('El archivo supera el máximo permitido de 1 MB.');
            return;
        }

        setError('');
        setUploading(true);
        try {
            const body = new FormData();
            body.append('file', file);
            const response = await fetch(IMPORT_URL, {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` },
                body,
            });

            if (response.ok) {
                const data = await response.json();
                setResult(data);
                if (data.imported > 0) onImported();
            } else if (response.status === 401) {
                onUnauthorized();
            } else if (response.status === 404) {
                setError('No tenés una librería activa.');
            } else {
                // 4xx bodies carry no message (RS31)
                setError('No pudimos procesar el archivo. Revisá que sea un CSV separado por ";", con las columnas indicadas y de hasta 1 MB. Intentalo de nuevo.');
            }
        } catch {
            setError(CONNECTION_ERROR);
        } finally {
            setUploading(false);
            setFile(null);
            inputRef.current.value = '';
        }
    };

    const errors = result?.errors ?? [];
    const hidden = errors.length - MAX_VISIBLE_ERRORS;

    return (
        <section className="csv-import" aria-labelledby="csv-import-title">
            <h2 id="csv-import-title" className="csv-import-title">Cargar libros desde CSV</h2>
            <p className="card-subtitle">Si alguna fila tiene errores, no se importa ninguna.</p>

            <form
                onSubmit={handleSubmit}
                className={`csv-dropzone${dragging ? ' is-dragging' : ''}`}
                onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                onDragLeave={() => setDragging(false)}
                onDrop={handleDrop}
            >
                <input
                    id="csv-file"
                    ref={inputRef}
                    className="visually-hidden"
                    type="file"
                    accept=".csv,text/csv"
                    onChange={(e) => pickFile(e.target.files[0])}
                    disabled={uploading}
                />
                <p className="csv-dropzone-text">
                    {file
                        ? <><strong>{file.name}</strong> · {Math.ceil(file.size / 1024)} KB</>
                        : 'Arrastrá tu archivo CSV acá'}
                </p>
                <div className="csv-dropzone-actions">
                    <label htmlFor="csv-file" className="btn-outline-pill">
                        {file ? 'Cambiar archivo' : 'Elegir archivo'}
                    </label>
                    <button type="submit" className="btn-submit-pill" disabled={!file || uploading}>
                        {uploading ? 'SUBIENDO...' : 'SUBIR CSV'}
                    </button>
                </div>
                <p className="csv-dropzone-hint">Solo archivos .csv de hasta 1 MB.</p>
            </form>

            <div className="csv-columns">
                <span>Columnas, separadas por <code>;</code></span>
                {COLUMNS.map((c) => <code key={c} className="csv-column-chip">{c}</code>)}
                <button type="button" className="btn-cancel" onClick={downloadTemplate}>Descargar plantilla</button>
            </div>

            {error && (
                <div className="alert-box alert-error" role="alert">
                    <span>{error}</span>
                </div>
            )}

            <div role="status" aria-live="polite">
                {result && (
                    <div className={`alert-box ${result.success ? 'alert-success' : 'alert-warning'} csv-import-result`}>
                        <span>
                            {result.success
                                ? `Se importaron ${result.imported} libros.`
                                : 'No se importó ningún libro. Corregí el archivo y volvé a subirlo.'}
                        </span>
                        {errors.length > 0 && (
                            <ul>
                                {errors.slice(0, MAX_VISIBLE_ERRORS).map((msg) => <li key={msg}>{msg}</li>)}
                                {hidden > 0 && <li>y {hidden} errores más.</li>}
                            </ul>
                        )}
                    </div>
                )}
            </div>
        </section>
    );
}
