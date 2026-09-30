import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

function App() {
  return (
    <main style={{ fontFamily: 'system-ui', padding: '3rem' }}>
      <h1>Orvexa</h1>
      <p>AI-native customer operations platform.</p>
      <p>Phase 0 boilerplate is ready.</p>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
