import { LitElement, html, css } from '../lib/lit.js';
// login-view.js
import { saveUser, setToken } from '../utils/storage.js';
import { enqueueOfflineRequest } from '../services/offline-queue.js';


export class LoginView extends LitElement {
  static properties = { error: { type: String } };

  constructor() { super(); this.error = ''; }

  async login(e) {
    e.preventDefault();
    this.error = '';
    const username = this.shadowRoot.querySelector('#username').value;
    const password = this.shadowRoot.querySelector('#password').value;

    try {
      const res = await fetch('/phoenix-iam/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!res.ok) { this.error = 'Invalid credentials'; return; }
      const data = await res.json();
      if (!data.token) { this.error = 'Invalid credentials'; return; }

      setToken(data.token);
      sessionStorage.setItem('currentUser', JSON.stringify({ username, email: data.email || `${username}@phoenix.com` }));
      this.dispatchEvent(new CustomEvent('navigate', { detail: 'user', bubbles: true, composed: true }));

    } catch { this.error = 'Server unreachable'; }
  }

  goToRegister() {
    this.dispatchEvent(new CustomEvent('navigate', { detail: 'register', bubbles: true, composed: true }));
  }

  render() {
    return html`
      <div class="container">
        <h2>🔐 Phoenix IAM</h2>
        <p class="subtitle">Welcome back! Please login to continue.</p>

        <form @submit=${this.login}>
          <div class="input-group">
            <label>Username</label>
            <input id="username" placeholder="Enter your username" required />
          </div>
          <div class="input-group">
            <label>Password</label>
            <input id="password" type="password" placeholder="Enter your password" required />
          </div>
          ${this.error ? html`<p class="error">❌ ${this.error}</p>` : ''}
          <button type="submit" class="btn-primary">Login</button>
        </form>

        <div class="register-section">
          <p>Don't have an account?</p>
          <button @click=${this.goToRegister} class="btn-secondary">Create Account</button>
        </div>
      </div>
    `;
  }

  static styles = css`
    :host { display: block; }
    .container { background: white; padding: 40px; border-radius: 20px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); max-width: 400px; margin: 0 auto; }
    h2 { text-align: center; color: #333; margin-bottom: 10px; font-size: 2rem; }
    .subtitle { text-align: center; color: #666; margin-bottom: 30px; }
    form { display: flex; flex-direction: column; gap: 20px; }
    .input-group { display: flex; flex-direction: column; gap: 8px; }
    label { font-weight: 600; color: #555; font-size: 0.9rem; }
    input { padding: 12px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 1rem; transition: border-color 0.3s; }
    input:focus { outline: none; border-color: #667eea; }
    .btn-primary { padding: 14px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; transition: transform 0.2s; }
    .btn-primary:hover { transform: translateY(-2px); }
    .btn-secondary { padding: 12px; background: white; color: #667eea; border: 2px solid #667eea; border-radius: 8px; font-size: 1rem; font-weight: 600; cursor: pointer; width: 100%; transition: all 0.3s; }
    .btn-secondary:hover { background: #667eea; color: white; }
    .error { color: #dc3545; background: #ffe6e6; padding: 12px; border-radius: 8px; text-align: center; font-weight: 500; }
    .register-section { margin-top: 30px; padding-top: 30px; border-top: 2px solid #f0f0f0; text-align: center; }
    .register-section p { color: #666; margin-bottom: 15px; }
  `;
}

customElements.define('login-view', LoginView);
