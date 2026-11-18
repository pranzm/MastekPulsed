/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/main.tsx
 * Journey: React 18 Application Bootstrap Entry Point for Mastek Campfire
 *          (Employee Web, Role-Based Publishing Portal, and Standalone Browser
 *          Simulation POC Shell).
 *
 * Why it is designed this way:
 *   1. REACT 18 CONCURRENT ROOT (`ReactDOM.createRoot`):
 *      - Uses the modern React 18 root API (`react-dom/client`) instead of the
 *        legacy `ReactDOM.render` so state updates inside `App.tsx` (such as
 *        persona switches or simultaneous API promises) benefit from automatic
 *        batching.
 *   2. STRICT MODE SAFETY (`<React.StrictMode>`):
 *      - Wraps `<App />` in `<React.StrictMode>` during development to catch
 *        impure renders, missing `useEffect` cleanup functions (like our audio
 *        progress timer in `App.tsx`), and accidental side effects early.
 *   3. GLOBAL TAILWIND & ACCESSIBILITY STYLES (`./index.css`):
 *      - Imports `index.css` once at the top level so WCAG 2.2 AA focus rings,
 *        Mastek Navy (`#002180`) brand tokens, and Tailwind utility classes are
 *        available across every child component.
 * ============================================================================
 */

// Import the core React library needed for JSX transformation and StrictMode.
import React from 'react';
// Import the React 18 DOM client renderer used to mount the virtual DOM into `#root`.
import ReactDOM from 'react-dom/client';
// Import the unified top-level Mastek Campfire application shell component.
import { App } from './App';
// Import global CSS rules, Tailwind directives, and keyboard focus styles.
import './index.css';

/**
 * Locate the `<div id="root"></div>` container inside `web/index.html` and
 * mount the React 18 component tree inside `<React.StrictMode>`.
 * The non-null assertion (`!`) tells TypeScript that `#root` is guaranteed
 * to exist in our HTML template.
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  // Enable React development checks for effect cleanup and side-effect safety.
  <React.StrictMode>
    {/* Render the top-level Mastek Campfire shell (Workspace switcher, Persona bar, Audio bar). */}
    <App />
  </React.StrictMode>
);
