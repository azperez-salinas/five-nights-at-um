import { useEffect, useState } from 'react';

const LIBRARIES_URL = 'http://localhost:8080/api/librerias';

const EMPTY_FILTERS = { libreria: '', autor: '' };

// R10: library and author filters, combinable. They apply on submit, not on every keystroke
export default function CatalogFilters({ filters, onApply }) {
    const [libraries, setLibraries] = useState([]);
    const [libreria, setLibreria] = useState(filters.libreria);
    const [autor, setAutor] = useState(filters.autor);

    useEffect(() => {
        const controller = new AbortController();
        fetch(LIBRARIES_URL, { signal: controller.signal })
            .then((response) => (response.ok ? response.json() : []))
            .then(setLibraries)
            // Without the list the author filter still works
            .catch(() => {});
        return () => controller.abort();
    }, []);

    const handleSubmit = (e) => {
        e.preventDefault();
        onApply({ libreria, autor: autor.trim() });
    };

    const handleClear = () => {
        setLibreria('');
        setAutor('');
        onApply(EMPTY_FILTERS);
    };

    const active = filters.libreria !== '' || filters.autor !== '';

    return (
        <form className="catalog-filters" onSubmit={handleSubmit}>
            <div className="input-group">
                <label htmlFor="filter-libreria">Librería</label>
                <select id="filter-libreria" value={libreria} onChange={(e) => setLibreria(e.target.value)}>
                    <option value="">Todas</option>
                    {libraries.map((lib) => (
                        <option key={lib.id} value={String(lib.id)}>{lib.nombre}</option>
                    ))}
                </select>
            </div>

            <div className="input-group">
                <label htmlFor="filter-autor">Autor</label>
                <input
                    id="filter-autor"
                    type="text"
                    placeholder="Ej: Cervantes"
                    maxLength={150}
                    value={autor}
                    onChange={(e) => setAutor(e.target.value)}
                />
            </div>

            <button type="submit" className="btn-submit-pill">FILTRAR</button>
            {active && (
                <button type="button" className="btn-cancel" onClick={handleClear}>
                    Limpiar filtros
                </button>
            )}
        </form>
    );
}
