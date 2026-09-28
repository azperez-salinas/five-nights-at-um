import { useCallback, useEffect, useState } from 'react';
import BookDetail from './BookDetail';
import Catalog from './Catalog';
import Profile from './Profile';
import Register from './Register';

// #catalogo -> 1, #catalogo/N -> N (N >= 1), anything else -> null
const parseCatalogPage = (hash) => {
  const match = hash.match(/^#catalogo(?:\/([1-9]\d{0,5}))?$/);
  return match ? Number(match[1] ?? 1) : null;
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
    window.location.hash = '#catalogo';
  };

  // Stable reference: Profile uses it as an effect dependency
  const handleUnauthorized = useCallback(() => setSession(null), []);

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
  return <Register onAuthenticated={handleAuthenticated} />;
}

export default App;
