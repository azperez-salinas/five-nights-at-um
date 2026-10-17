import { useState } from 'react';

// The initial theme is set before first paint by the inline script in index.html
export function useTheme() {
    const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || 'light');

    const toggle = () => {
        const next = theme === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try {
            localStorage.setItem('theme', next);
        } catch {
            // storage unavailable (private mode): the theme still applies for this session
        }
        setTheme(next);
    };

    return [theme, toggle];
}
