import { useCallback, useEffect, useRef, useState } from 'react';
import AdminPanel from './AdminPanel';
import BookDetail from './BookDetail';
import Catalog from './Catalog';
import Favorites from './Favorites';
import Login from './Login';
import MyLibrary from './MyLibrary';
import Profile from './Profile';
import Register from './Register';
import BookSearch from './BookSearch'
import ChangePassword from './ChangePassword';

const LOGOUT_URL = 'http://localhost:8080/api/auth/logout';

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
  // Message shown on the login screen (e.g. after replacing a temporary password)
  const [loginNotice, setLoginNotice] = useState('');
  // The hashchange listener is registered once, so it reads the current session from here
  const sessionRef = useRef(null);

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const handleLogout = useCallback(async () => {
    // R3: revoca el token en el backend y limpia la sesion en el cliente
    const token = sessionRef.current?.token;
    if (token) {
      try {
        await fetch(LOGOUT_URL, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
      } catch (err) {
        console.error('Error al revocar token en backend:', err);
      }
    }
    setSession(null);
    goTo('#login');
  }, []);

  useEffect(() => {
    const onHashChange = () => {
      const newHash = window.location.hash;
      // RS18: logging out drops the token from the client
      if (newHash === '#logout') {
        handleLogout();
        return;
      }
      setHash(newHash);
      if (isListHash(newHash)) setLastListHash(newHash);
      window.scrollTo(0, 0);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, [handleLogout]);

  const handleAuthenticated = (auth) => {
    setSession(auth);
    goTo('#catalogo');
  };

  // R18: the admin goes to its panel and an owner with a temporary password must replace it first.
  // Logging in from #favoritos keeps the user there; otherwise go to the catalog
  const handleLoginSuccess = (token, username, role, mustChangePassword) => {
    setSession({ token, username, role, mustChangePassword });
    setLoginNotice('');
    if (mustChangePassword) goTo('#cambiar-password');
    else if (role === 'ADMIN') goTo('#admin');
    else if (hash !== '#favoritos') goTo('#catalogo');
  };

  // Stable reference: Profile uses it as an effect dependency
  const handleUnauthorized = useCallback(() => setSession(null), []);

  // R18: the backend revoked the temporary token, so the owner logs in again with the new password
  const handlePasswordChanged = () => {
    setSession(null);
    setLoginNotice('Tu contraseña se actualizó. Iniciá sesión con la nueva contraseña.');
    goTo('#login');
  };

  // R18: these sessions are confined to a single screen whatever the hash says
  // (the backend rejects anything else with 403 anyway)
  if (session?.mustChangePassword) {
    return (
      <ChangePassword
        session={session}
        onPasswordChanged={handlePasswordChanged}
        onUnauthorized={handleUnauthorized}
        onLogout={handleLogout}
      />
    );
  }
  if (session?.role === 'ADMIN') {
    return (
      <AdminPanel
        session={session}
        onUnauthorized={handleUnauthorized}
        onLogout={handleLogout}
      />
    );
  }

  const catalogPage = parseCatalogPage(hash);
  if (catalogPage) {
    return (
      <Catalog
        page={catalogPage}
        session={session}
        filters={catalogFilters}
        onFiltersChange={setCatalogFilters}
        onLogout={handleLogout}
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
        onLogout={handleLogout}
      />
    );
  }
  if (hash === '#perfil') {
    return (
      <Profile
        session={session}
        onUnauthorized={handleUnauthorized}
        onLogout={handleLogout}
      />
    );
  }
  // R19: owners only; any other role falls through to the default screen
  if (hash === '#mi-libreria' && session?.role === 'DUENO') {
    return (
      <MyLibrary
        session={session}
        onUnauthorized={handleUnauthorized}
        onLogout={handleLogout}
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

  const needsLogin = hash === '#favoritos' || hash === '#admin' || hash === '#cambiar-password' || hash === '#mi-libreria';
  if (hash === '#login' || (needsLogin && !session)) {
    return (
      <Login
        notice={loginNotice}
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
      onLogout={handleLogout}
    />
  );
}

export default App;
