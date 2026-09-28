import { useCallback, useEffect, useState } from 'react';
import Catalog from './Catalog';
import Profile from './Profile';
import Register from './Register';

// ponytail: hash navigation for a few screens, switch to react-router when routes need params
function App() {
  const [hash, setHash] = useState(window.location.hash);
  // Session lives in memory only (not localStorage) so an XSS cannot read the token
  // from storage; the trade-off is that a page reload ends the session.
  const [session, setSession] = useState(null);

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  const handleAuthenticated = (auth) => {
    setSession(auth);
    window.location.hash = '#catalogo';
  };

  // Stable reference: Profile uses it as an effect dependency
  const handleUnauthorized = useCallback(() => setSession(null), []);

  if (hash === '#catalogo') return <Catalog session={session} />;
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
