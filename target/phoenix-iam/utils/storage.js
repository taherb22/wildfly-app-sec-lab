// utils/storage.js
const USER_KEY = 'currentUser';
const TOKEN_KEY = 'phoenixToken';

// Simple hashing using SHA-256 (browser crypto)
async function hashToken(token) {
  const enc = new TextEncoder();
  const buffer = await crypto.subtle.digest('SHA-256', enc.encode(token));
  return Array.from(new Uint8Array(buffer))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

/** TOKEN FUNCTIONS **/
export async function setToken(token) {
  const hashed = await hashToken(token);
  localStorage.setItem(TOKEN_KEY, hashed);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY); // returns hashed value
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/** USER FUNCTIONS **/
export function saveUser(user) {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function getUser() {
  const str = sessionStorage.getItem(USER_KEY);
  return str ? JSON.parse(str) : null;
}

export function clearUser() {
  sessionStorage.removeItem(USER_KEY);
}
