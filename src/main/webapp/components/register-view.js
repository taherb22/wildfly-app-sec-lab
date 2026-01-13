import { LitElement, html, css } from '../lib/lit.js';
import { sanitize } from '../utils/sanitize.js';
import { enqueueOfflineRequest } from '../services/offline-queue.js';


export class RegisterView extends LitElement {
  static properties = {
    error: { type: String },
    success: { type: String }
  };

  constructor() {
    super();
    this.error = '';
    this.success = '';
  }

  async register(e) {
    e.preventDefault();
    this.error = '';
    this.success = '';

    const form = e.target;
    const name = sanitize(form.name.value.trim());
    const email = sanitize(form.email.value.trim());
    const password = form.password.value;
    const confirmPassword = form.confirmPassword.value;

    if (!name || !email || !password) {
      this.error = 'Please fill in all fields';
      return;
    }

    if (password !== confirmPassword) {
      this.error = 'Passwords do not match';
      return;
    }

    if (password.length < 6) {
      this.error = 'Password must be at least 6 characters';
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      this.error = 'Please enter a valid email address';
      return;
    }

    try {
      const res = await fetch('/phoenix-iam/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: name, email, password })
      });

      const data = await res.json();
      if (!res.ok) {
        this.error = data.message || 'Registration failed';
        return;
      }

      this.success = '✅ Account created successfully!';
      form.reset();

      setTimeout(() => this.goToLogin(), 2000);
    } catch (err) {
      this.error = 'Server unreachable. Please try again.';
      console.error(err);
    }
  }

  goToLogin() {
    this.dispatchEvent(new CustomEvent('navigate', {
      detail: 'login',
      bubbles: true,
      composed: true
    }));
  }

  render() {
    return html`
      <div class="container">
        <h2>✨ Create Account</h2>
        <p class="subtitle">Join Phoenix IAM!</p>
        ${this.success ? html`<div class="success">${this.success}</div>` : ''}
        ${this.error ? html`<div class="error">❌ ${this.error}</div>` : ''}

        <form @submit=${this.register}>
          <div class="input-group">
            <label>Full Name</label>
            <input type="text" name="name" placeholder="Enter full name" required />
          </div>
          <div class="input-group">
            <label>Email Address</label>
            <input type="email" name="email" placeholder="your.email@example.com" required />
          </div>
          <div class="input-group">
            <label>Password</label>
            <input type="password" name="password" placeholder="At least 6 characters" required />
          </div>
          <div class="input-group">
            <label>Confirm Password</label>
            <input type="password" name="confirmPassword" placeholder="Re-enter password" required />
          </div>

          <button type="submit" class="btn-primary">Create Account</button>
        </form>

        <div class="login-section">
          <p>Already have an account?</p>
          <button @click=${this.goToLogin} class="btn-secondary">Back to Login</button>
        </div>
      </div>
    `;
  }

  static styles = css`
    :host { display: block; }
    .container {
      background: white; padding: 40px; border-radius: 20px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.1); max-width: 400px; margin: 0 auto;
    }
    h2 { text-align: center; color: #333; margin-bottom: 10px; font-size: 2rem; }
    .subtitle { text-align: center; color: #666; margin-bottom: 30px; }
    .input-group { display: flex; flex-direction: column; gap: 8px; }
    label { font-weight: 600; color: #555; font-size: 0.9rem; }
    input { padding: 12px; border: 2px solid #e0e0e0; border-radius: 8px; font-size: 1rem; transition: border-color 0.3s; }
    input:focus { outline: none; border-color: #667eea; }
    .btn-primary {
      padding: 14px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; transition: transform 0.2s;
    }
    .btn-primary:hover { transform: translateY(-2px); }
    .btn-secondary {
      padding: 12px; background: white; color: #667eea; border: 2px solid #667eea;
      border-radius: 8px; font-weight: 600; cursor: pointer; width: 100%; transition: all 0.3s;
    }
    .btn-secondary:hover { background: #667eea; color: white; }
    .error { color: #dc3545; background: #ffe6e6; padding: 12px; border-radius: 8px; text-align: center; font-weight: 500; margin-bottom: 20px; }
    .success { color: #28a745; background: #d4edda; padding: 12px; border-radius: 8px; text-align: center; font-weight: 500; margin-bottom: 20px; }
    .login-section { margin-top: 30px; padding-top: 30px; border-top: 2px solid #f0f0f0; text-align: center; }
    .login-section p { color: #666; margin-bottom: 15px; }
  `;
}

customElements.define('register-view', RegisterView);
