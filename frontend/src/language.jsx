import { createContext, useContext, useState } from 'react';

export const translations = {
    es: {
        nav_featured: 'DESTACADOS',
        nav_search: 'BUSCAR',
        nav_favorites: 'FAVORITOS',
        nav_my_library: 'MI LIBRERÍA',
        nav_admin: 'PANEL',
        nav_logout: 'CERRAR SESIÓN',
        nav_login: 'INICIAR SESIÓN',
        nav_register: 'REGISTRARSE',
        nav_tagline: 'LIBRERÍA & EDITORIAL',
        theme_light: 'Activar tema claro',
        theme_dark: 'Activar tema oscuro',
        lang_toggle_label: 'Cambiar idioma',

        // Catálogo y filtros
        catalog_title: 'Destacados',
        catalog_loading: 'Cargando libros...',
        catalog_empty_filtered: 'No hay libros que coincidan con los filtros elegidos.',
        catalog_empty: 'No hay libros disponibles por el momento.',
        catalog_prev: 'ANTERIOR',
        catalog_next: 'SIGUIENTE',
        catalog_page: 'Página',
        catalog_of: 'de',
        catalog_found_single: 'libro encontrado con los filtros seleccionados.',
        catalog_found_plural: 'libros encontrados con los filtros seleccionados.',
        filter_library: 'Librería',
        filter_all: 'Todas',
        filter_author: 'Autor',
        filter_author_placeholder: 'Ej: Cervantes',
        filter_btn: 'FILTRAR',
        filter_btn_loading: 'FILTRANDO...',
        filter_clear: 'Limpiar filtros',
        fav_add: 'Agregar a favoritos',
        fav_remove: 'Quitar de favoritos',

        // Favoritos
        fav_title: 'Mis Libros Favoritos',
        fav_desc: 'Tus obras seleccionadas y lecturas pendientes guardadas en tu cuenta.',
        fav_empty_title: 'Aún no tenés libros guardados',
        fav_empty_desc: 'Mirá los destacados y tocá el corazón para armar tu lista de lecturas preferidas.',
        fav_btn_catalog: 'VER DESTACADOS',
        fav_loading: 'Consultando catálogo editorial...',

        // Búsqueda
        search_main_title: 'Búsqueda en Catálogo',
        search_subtitle: 'Encontrá obras de nuestra colección ingresando título, nombre de autor o número de ISBN.',
        search_placeholder: 'Buscar por título, autor o ISBN...',
        search_clear: 'Limpiar búsqueda',
        search_loading: 'Buscando en los registros editoriales...',
        search_no_matches: 'Sin coincidencias',
        search_view_all: 'VER TODO EL CATÁLOGO',
        search_results_for: 'Resultados para',
        search_book_found: 'libro encontrado',
        search_books_found: 'libros encontrados',
        search_exploring: 'Explorando catálogo editorial',
        search_titles_available: 'títulos disponibles',

        // Perfil
        profile_title: 'Mi perfil',
        profile_user: 'Usuario:',
        profile_email: 'Correo electrónico:',
        profile_change_pass: 'CAMBIAR CONTRASEÑA',
        profile_current_pass: 'Contraseña actual',
        profile_new_pass: 'Nueva contraseña',
        profile_min_chars: 'Mínimo 8 caracteres',
        profile_confirm_pass: 'Confirmar nueva contraseña',
        profile_save_btn: 'GUARDAR CAMBIOS',
        profile_saving: 'GUARDANDO...',
        profile_cancel: 'Cancelar',
        profile_loading: 'Cargando perfil...',

        // Detalle de libro
        book_year: 'Año',
        book_pages: 'Páginas',
        book_loading: 'Cargando libro...',
        book_back_to: '← VOLVER A',
    },
    en: {
        nav_featured: 'HIGHLIGHTS',
        nav_search: 'SEARCH',
        nav_favorites: 'FAVORITES',
        nav_my_library: 'MY BOOKSTORE',
        nav_admin: 'PANEL',
        nav_logout: 'LOG OUT',
        nav_login: 'LOG IN',
        nav_register: 'REGISTER',
        nav_tagline: 'BOOKSTORE & PUBLISHER',
        theme_light: 'Switch to light mode',
        theme_dark: 'Switch to dark mode',
        lang_toggle_label: 'Switch language',

        // Catalog and filters
        catalog_title: 'Highlights',
        catalog_loading: 'Loading books...',
        catalog_empty_filtered: 'No books match the selected filters.',
        catalog_empty: 'No books available at the moment.',
        catalog_prev: 'PREVIOUS',
        catalog_next: 'NEXT',
        catalog_page: 'Page',
        catalog_of: 'of',
        catalog_found_single: 'book found with selected filters.',
        catalog_found_plural: 'books found with selected filters.',
        filter_library: 'Bookstore',
        filter_all: 'All',
        filter_author: 'Author',
        filter_author_placeholder: 'e.g. Cervantes',
        filter_btn: 'FILTER',
        filter_btn_loading: 'FILTERING...',
        filter_clear: 'Clear filters',
        fav_add: 'Add to favorites',
        fav_remove: 'Remove from favorites',

        // Favorites
        fav_title: 'My Favorite Books',
        fav_desc: 'Your selected works and saved reading list in your account.',
        fav_empty_title: "You haven't saved any books yet",
        fav_empty_desc: 'Browse highlights and tap the heart icon to build your reading list.',
        fav_btn_catalog: 'VIEW HIGHLIGHTS',
        fav_loading: 'Querying bookstore catalog...',

        // Search
        search_main_title: 'Catalog Search',
        search_subtitle: 'Find works in our collection by entering title, author name or ISBN.',
        search_placeholder: 'Search by title, author, or ISBN...',
        search_clear: 'Clear search',
        search_loading: 'Searching editorial records...',
        search_no_matches: 'No matches found',
        search_view_all: 'VIEW FULL CATALOG',
        search_results_for: 'Results for',
        search_book_found: 'book found',
        search_books_found: 'books found',
        search_exploring: 'Exploring editorial catalog',
        search_titles_available: 'titles available',

        // Profile
        profile_title: 'My Profile',
        profile_user: 'Username:',
        profile_email: 'Email address:',
        profile_change_pass: 'CHANGE PASSWORD',
        profile_current_pass: 'Current password',
        profile_new_pass: 'New password',
        profile_min_chars: 'Minimum 8 characters',
        profile_confirm_pass: 'Confirm new password',
        profile_save_btn: 'SAVE CHANGES',
        profile_saving: 'SAVING...',
        profile_cancel: 'Cancel',
        profile_loading: 'Loading profile...',

        // Book detail
        book_year: 'Year',
        book_pages: 'Pages',
        book_loading: 'Loading book...',
        book_back_to: '← BACK TO',
    },
};

const LanguageContext = createContext(null);

export function LanguageProvider({ children }) {
    const [lang, setLangState] = useState(() => {
        try {
            return localStorage.getItem('buysan_lang') || 'es';
        } catch {
            return 'es';
        }
    });

    const setLang = (next) => {
        const selected = next === 'en' ? 'en' : 'es';
        setLangState(selected);
        try {
            localStorage.setItem('buysan_lang', selected);
        } catch {
            // Modo privado / almacenamiento no disponible
        }
    };

    const toggleLang = () => {
        setLang(lang === 'es' ? 'en' : 'es');
    };

    const t = (key) => {
        return translations[lang]?.[key] || translations.es?.[key] || key;
    };

    return (
        <LanguageContext.Provider value={{ lang, setLang, toggleLang, t }}>
            {children}
        </LanguageContext.Provider>
    );
}

export function useLanguage() {
    const context = useContext(LanguageContext);
    if (!context) {
        throw new Error('useLanguage debe usarse dentro de un LanguageProvider');
    }
    return context;
}

export function LanguageToggle() {
    const { lang, toggleLang, t } = useLanguage();
    const nextLang = lang === 'es' ? 'EN' : 'ES';

    return (
        <button
            type="button"
            className="nav-item nav-lang-toggle"
            onClick={toggleLang}
            aria-label={t('lang_toggle_label')}
            title={t('lang_toggle_label')}
            style={{
                background: 'none',
                border: '1px solid currentColor',
                borderRadius: '4px',
                padding: '2px 7px',
                fontSize: '11px',
                fontWeight: 600,
                letterSpacing: '0.05em',
                cursor: 'pointer',
                lineHeight: '1.2',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}
        >
            {nextLang}
        </button>
    );
}
