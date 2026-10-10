import { useCallback, useEffect, useState } from 'react';
import BookDetail from './BookDetail';
import Catalog from './Catalog';
import Favorites from './Favorites';
import Login from './Login';
import Profile from './Profile';
import Register from './Register';
import BookSearch from './BookSearch'

// R10: no filter applied
const EMPTY_FILTERS = { libreria: '', autor: '' };

// #catalogo -> 1, #catalogo/N -> N (N >= 1), anything else -> null
const parseCatalogPage = (hash) => {
  const match = hash.match(/^#catalogo(?:\/([1-9]\d{0,5}))?$/);
  return match ? Number(match[1] ?? 1) : null;
};

// Screens that list books; the book detail links back to the last one visited
const isListHash = (hash) => Boolean(parseCatalogPage(hash)) || hash === '#buscar' || hash === '#favoritos';

const BACK_TARGETS = {
  '#buscar': { nav: 'buscar', label: 'BÚSQUEDA' },
  '#favoritos': { nav: 'favoritos', label: 'FAVORITOS' },
};
const CATALOG_BACK = { nav: 'catalogo', label: 'DESTACADOS' };

const goTo = (hash) => {
  window.location.hash = hash;
};

// ponytail: hand-rolled hash routes, switch to react-router when routes multiply
function App() {
  const [hash, setHash] = useState(window.location.hash);
  // Last list screen visited (#catalogo/N, #buscar or #favoritos) for the detail's back link
  const [lastListHash, setLastListHash] = useState(
    isListHash(window.location.hash) ? window.location.hash : '#catalogo'
  );
  // Kept here so the search text survives going to a book detail and back
  const [searchQuery, setSearchQuery] = useState('');
  // R10: same idea for the catalog filters
  const [catalogFilters, setCatalogFilters] = useState(EMPTY_FILTERS);
  // Session lives in memory only (not localStorage) so an XSS cannot read the token
  // from storage; the trade-off is that a page reload ends the session.
  const [session, setSession] = useState(null);

  useEffect(() => {
    const onHashChange = () => {
      const newHash = window.location.hash;
      // RS18: logging out drops the token from the client
      if (newHash === '#logout') {
        setSession(null);
        window.location.replace('#login');
        return;
      }
      setHash(newHash);
      if (isListHash(newHash)) setLastListHash(newHash);
      window.scrollTo(0, 0);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  const handleAuthenticated = (auth) => {
    setSession(auth);
    goTo('#catalogo');
  };

  // Logging in from #favoritos keeps the user there; otherwise go to the catalog
  const handleLoginSuccess = (token, username, role) => {
    setSession({ token, username, role });
    if (hash !== '#favoritos') goTo('#catalogo');
  };

  // Stable reference: Profile uses it as an effect dependency
  const handleUnauthorized = useCallback(() => setSession(null), []);

  const handleLogout = () => goTo('#logout');

  const catalogPage = parseCatalogPage(hash);
  if (catalogPage) {
    return (
      <Catalog
        page={catalogPage}
        session={session}
        filters={catalogFilters}
        onFiltersChange={setCatalogFilters}
      />
    );
  }
  // Only numeric ids reach the API; anything else falls through to the default screen
  const bookMatch = hash.match(/^#libro\/(\d{1,18})$/);
  if (bookMatch) {
    const back = BACK_TARGETS[lastListHash] ?? CATALOG_BACK;
    return (
      <BookDetail
        bookId={Number(bookMatch[1])}
        backHref={lastListHash}
        backLabel={back.label}
        navActive={back.nav}
        session={session}
      />
    );
  }
  if (hash === '#perfil') {
    return (
      <Profile
        session={session}
        onUnauthorized={handleUnauthorized}
      />
    );
  }
  if (hash === '#buscar') {
    return (
      <BookSearch
        session={session}
        onLogout={handleLogout}
        initialQuery={searchQuery}
        onQueryChange={setSearchQuery}
      />
    );
  }

  if (hash === '#login' || (hash === '#favoritos' && !session)) {
    return (
      <Login
        onLoginSuccess={handleLoginSuccess}
        onNavigateToRegister={() => goTo('#registro')}
      />
    );
  }
  if (hash === '#favoritos') {
    return (
      <Favorites
        session={session}
        onLogout={handleLogout}
        onNavigate={(dest) => goTo(`#${dest}`)}
      />
    );
  }
  if (hash === '#registro') return <Register onAuthenticated={handleAuthenticated} />;
  // Visitors can browse without an account: the catalog is the landing page for everyone
  return (
    <Catalog
      page={1}
      session={session}
      filters={catalogFilters}
      onFiltersChange={setCatalogFilters}
    />
  );
}

export default App;
