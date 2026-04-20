import { apiUpload } from './client.js';

/**
 * Upload a document to the knowledge base.
 * The backend saves it to the knowledge folder and immediately indexes it into PgVector.
 * @param {File} file - Browser File object
 * @returns {Promise<string>} Success message from backend
 */
export const uploadKnowledgeDocument = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/knowledge/upload', formData);
};
