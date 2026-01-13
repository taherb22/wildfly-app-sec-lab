export async function login(username, password) {
  return fetch('/phoenix-iam/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  }).then(r => r.json());
}