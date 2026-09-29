import { useCallback, useEffect, useState } from 'react';
import BookDetail from './BookDetail';
import Catalog from './Catalog';
import Favorites from './Favorites';
import Login from './Login';
import Profile from './Profile';
import Register from './Register';
import BookSearch from './BookSearch'

// #catalogo -> 1, #catalogo/N -> N (N >= 1), anything else -> null
const parseCatalogPage = (hash) => {
  const match = hash.match(/^#catalogo(?:\/([1-9]\d{0,5}))?$/);
  return match ? Number(match[1] ?? 1) : null;
};

const goTo = (hash) => {
  window.location.hash = hash;
};

// ponytail: hand-rolled hash routes, switch to react-router when routes multiply
function App() {
  const [hash, setHash] = useState(window.location.hash);
  // Last catalog page visited, so the book detail can link back to it
  const [lastCatalogPage, setLastCatalogPage] = useState(parseCatalogPage(window.location.hash) ?? 1);
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
      const catalogPage = parseCatalogPage(newHash);
      if (catalogPage) setLastCatalogPage(catalogPage);
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
  const handleLoginSuccess = (token, username) => {
    setSession({ token, username });
    if (hash !== '#favoritos') goTo('#catalogo');
  };

  // Stable reference: Profile uses it as an effect dependency
  const handleUnauthorized = useCallback(() => setSession(null), []);

  const handleLogout = () => goTo('#logout');

  const catalogPage = parseCatalogPage(hash);
  if (catalogPage) return <Catalog page={catalogPage} session={session} />;
  // Only numeric ids reach the API; anything else falls through to the default screen
  const bookMatch = hash.match(/^#libro\/(\d{1,18})$/);
  if (bookMatch) {
    return (
      <BookDetail
        bookId={Number(bookMatch[1])}
        backHref={`#catalogo/${lastCatalogPage}`}
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
        token={session?.token}
        currentUser={session?.username}
        onLogout={handleLogout}
        onNavigate={(dest) => goTo(`#${dest}`)}
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
        token={session.token}
        currentUser={session.username}
        onLogout={handleLogout}
        onNavigate={(dest) => goTo(`#${dest}`)}
      />
    );
  }
  if (session) return <Catalog page={1} session={session} />;
  return <Register onAuthenticated={handleAuthenticated} />;
}

export default App;
