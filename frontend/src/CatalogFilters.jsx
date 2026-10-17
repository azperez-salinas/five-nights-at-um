import { useEffect, useState } from 'react';
import { useLanguage } from './language';

const LIBRARIES_URL = 'http://localhost:8080/api/librerias';

const EMPTY_FILTERS = { libreria: '', autor: '' };

// R10: library and author filters, combinable. They apply on submit, not on every keystroke
export default function CatalogFilters({ filters, onApply, loading = false }) {
    const { t } = useLanguage();
    const [libraries, setLibraries] = useState([]);
    const [libreria, setLibreria] = useState(filters.libreria);
    const [autor, setAutor] = useState(filters.autor);
    const [prevFilters, setPrevFilters] = useState(filters);

    // Mantiene los inputs sincronizados si los filtros cambian desde afuera (ej. navegación).
    // Se ajusta durante el render, no en un efecto, para evitar un render extra
    if (prevFilters.libreria !== filters.libreria || prevFilters.autor !== filters.autor) {
        setPrevFilters(filters);
        setLibreria(filters.libreria);
        setAutor(filters.autor);
    }

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
                <label htmlFor="filter-libreria">{t('filter_library')}</label>
                <select id="filter-libreria" value={libreria} onChange={(e) => setLibreria(e.target.value)}>
                    <option value="">{t('filter_all')}</option>
                    {libraries.map((lib) => (
                        <option key={lib.id} value={String(lib.id)}>{lib.nombre}</option>
                    ))}
                </select>
            </div>

            <div className="input-group">
                <label htmlFor="filter-autor">{t('filter_author')}</label>
                <input
                    id="filter-autor"
                    type="text"
                    placeholder={t('filter_author_placeholder')}
                    maxLength={150}
                    value={autor}
                    onChange={(e) => setAutor(e.target.value)}
                />
            </div>

            <button type="submit" className="btn-submit-pill" disabled={loading}>
                {loading ? t('filter_btn_loading') : t('filter_btn')}
            </button>
            {active && (
                <button type="button" className="btn-cancel" onClick={handleClear} disabled={loading}>
                    {t('filter_clear')}
                </button>
            )}
        </form>
    );
}
