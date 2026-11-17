import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/main.tsx
 * Purpose: React 18 Entry Point mounting the Mastek Campfire Web & Portal App.
 * ============================================================================
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
