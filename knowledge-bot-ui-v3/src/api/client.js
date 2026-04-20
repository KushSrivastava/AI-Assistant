/**
 * Base API client — all fetch calls go through here.
 * Handles: base URL, JSON headers, error parsing, 428 workspace-required.
 */

const BASE = '/api/v1';

// Custom error classes so the UI knows how to react
export class WorkspaceRequiredError extends Error {
  constructor() { super('WORKSPACE_REQUIRED'); }
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/**
 * Core fetch wrapper.
 * @param {string} path - e.g. '/workspace/status'
 * @param {RequestInit} options
 */
export async function apiFetch(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });

  // 428 = workspace not attached
  if (res.status === 428) {
    throw new WorkspaceRequiredError();
  }

  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      msg = body.message || body.error || msg;
    } catch (_) { /* body wasn't JSON */ }
    throw new ApiError(res.status, msg);
  }

  // Return raw response so caller can choose .json() / .text() / stream
  return res;
}

/**
 * Convenience: fetch + parse JSON
 */
export async function apiGet(path) {
  const res = await apiFetch(path, { method: 'GET' });
  return res.json();
}

export async function apiPost(path, body) {
  const res = await apiFetch(path, {
    method: 'POST',
    body: JSON.stringify(body)
  });
  if (res.status === 204) return null;
  return res.json();
}

/**
 * Multipart form upload — do NOT set Content-Type (browser sets boundary)
 */
export async function apiUpload(path, formData) {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    body: formData
  });
  if (res.status === 428) throw new WorkspaceRequiredError();
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, text);
  }
  return res.text();
}
