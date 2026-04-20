import { apiPost } from './client.js';

/**
 * Generate HLD only. Returns { hld, savedTo }.
 * Takes 1-3 minutes for complex goals.
 */
export const generateHld = (goal) =>
  apiPost('/architect/hld', { goal });

/**
 * Generate LLD from an existing HLD. Returns { lld, savedTo }.
 */
export const generateLld = (hld) =>
  apiPost('/architect/lld', { hld });
