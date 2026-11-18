import { registerRootComponent } from 'expo';
import App from './App';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: mobile/index.js
 * Journey: Android & iOS Mobile Entry Point (Expo SDK 58 + React Native 0.88).
 * Why it is designed this way:
 *   `registerRootComponent(App)` registers the root React Native component with
 *   `AppRegistry.registerComponent('main', () => App)` so Expo Go and native
 *   iOS/Android builds mount `App.tsx` consistently.
 * ============================================================================
 */
registerRootComponent(App);
