// app.js
import './components/app-shell.js';
import './components/login-view.js';
import './components/register-view.js';
import './components/user-view.js';
import { enqueueOfflineRequest, processQueue } from './services/offline-queue.js';
import { setToken, getToken } from './utils/storage.js';
import { sanitize } from './utils/sanitize.js';

// Register Service Worker
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js')
    .then(reg => console.log('Service Worker registered:', reg))
    .catch(err => console.error('SW registration failed:', err));
}

// Get the app shell element
const appShell = document.querySelector('app-shell');

// Listen for navigation events from components
appShell.addEventListener('navigate', e => {
  const target = e.detail;

  switch(target) {
    case 'login':
      appShell.currentView = 'login-view';
      break;
    case 'register':
      appShell.currentView = 'register-view';
      break;
    case 'user':
      appShell.currentView = 'user-view';
      break;
    default:
      appShell.currentView = 'login-view';
  }
});

// Helper function to call login API
export async function loginUser(username, password) {
  try {
    const response = await fetch('/phoenix-iam/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: sanitize(username), password: sanitize(password) })
    });

    if (!response.ok) {
      return { success: false, error: `HTTP ${response.status}` };
    }

    const data = await response.json();
    if (data.token) {
      setToken(data.token); // store token securely
      appShell.currentView = 'user-view'; // navigate to user dashboard
      return { success: true };
    } else {
      return { success: false, error: data.error || 'Invalid credentials' };
    }

  } catch (err) {
    console.error('Login failed:', err);
    return { success: false, error: err.message };
  }
}

// Helper function to logout
export function logoutUser() {
  setToken(null);
  appShell.currentView = 'login-view';
}

// Listen for service worker messages
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.addEventListener('message', event => {
    if (event.data?.type === 'SYNC_OFFLINE_QUEUE') {
      // Use the correct function from offline-queue
      processQueue()
        .then(() => console.log('Offline queue processed successfully'))
        .catch(err => console.error('Offline queue processing failed:', err));
    }
  });
}
