import { useState } from 'react';
import Register from './Register';
import Login from './Login';
import Favorites from './Favorites';

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token') || '');
  const [currentUser, setCurrentUser] = useState(() => localStorage.getItem('username') || '');
  const [view, setView] = useState(() => token ? 'favorites' : 'login'); // 'login' | 'register' | 'favorites'

  const handleLoginSuccess = (newToken, newUsername) => {
    setToken(newToken);
    setCurrentUser(newUsername);
    localStorage.setItem('token', newToken);
    localStorage.setItem('username', newUsername);
    setView('favorites');
  };

  const handleLogout = () => {
    setToken('');
    setCurrentUser('');
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    setView('login');
  };

  if (view === 'register') {
    return (
      <Register
        onNavigateToLogin={() => setView('login')}
        onNavigateToFavorites={() => setView('favorites')}
      />
    );
  }

  if (view === 'login') {
    return (
      <Login
        onLoginSuccess={handleLoginSuccess}
        onNavigateToRegister={() => setView('register')}
      />
    );
  }

  return (
    <Favorites
      token={token}
      currentUser={currentUser}
      onLogout={handleLogout}
      onNavigate={(dest) => setView(dest)}
    />
  );
}
