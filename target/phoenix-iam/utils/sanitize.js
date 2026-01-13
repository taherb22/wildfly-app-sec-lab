import DOMPurify from '../lib/purify.es.js';

export function sanitize(str) {
  return DOMPurify.sanitize(str);
}
