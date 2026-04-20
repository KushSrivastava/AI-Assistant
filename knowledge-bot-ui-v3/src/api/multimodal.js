import { apiUpload } from './client.js';

/**
 * Upload a UI screenshot / wireframe image and get back generated code.
 * Uses Ollama's vision model (llava / qwen-vl) locally.
 * @param {File} image - Browser File object (png/jpg)
 * @param {string} prompt - What to build, e.g. "Create a React component for this login form"
 * @returns {Promise<string>} Generated code as a string
 */
export const analyzeImageToCode = (image, prompt) => {
  const formData = new FormData();
  formData.append('image', image);
  formData.append('prompt', prompt);
  return apiUpload('/chat/ui-to-code', formData);
};
