import { apiGet, apiPost } from './client.js';

/** Attach a workspace directory on the backend. */
export const attachWorkspace = (path) =>
  apiPost('/workspace/attach', { path });

/** Get current workspace status. Returns { attached: bool, path: string } */
export const getWorkspaceStatus = () =>
  apiGet('/workspace/status');

/** Detach the active workspace. */
export const detachWorkspace = () =>
  apiPost('/workspace/detach', {});
