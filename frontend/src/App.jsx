import { useEffect, useState } from 'react';
import Catalog from './Catalog';
import Register from './Register';

// ponytail: hash navigation for two screens, switch to react-router when routes need params
function App() {
  const [hash, setHash] = useState(window.location.hash);

  useEffect(() => {
    const onHashChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  return hash === '#catalogo' ? <Catalog /> : <Register />;
}

export default App;
