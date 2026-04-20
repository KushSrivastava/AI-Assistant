import { apiPost } from './client.js';

/**
 * Open a file in the user's preferred editor.
 * Proxied via Vite to avoid CORS (IdeController lacks @CrossOrigin).
 * @param {string} path - Relative path to file in workspace
 * @param {'vscode' | 'intellij'} editor - preferred editor
 */
export const openInIde = (path, editor = 'vscode') =>
  apiPost('/ide/open', { path, editor });
