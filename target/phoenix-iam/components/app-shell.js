import { LitElement, html, css } from '../lib/lit.js';
import './login-view.js';
import './register-view.js';
import './user-view.js';
import { getToken } from '../utils/storage.js';

export class AppShell extends LitElement {
  static properties = { page: { type: String } };

  constructor() {
    super();
    this.page = getToken() ? 'user' : 'login';
    this.addEventListener('navigate', e => { this.page = e.detail; });
  }

  render() {
    return html`
      <div class="shell">
        ${this.page === 'login' ? html`<login-view></login-view>` : ''}
        ${this.page === 'register' ? html`<register-view></register-view>` : ''}
        ${this.page === 'user' ? html`<user-view></user-view>` : ''}
      </div>
    `;
  }

  static styles = css`
    :host { display: block; min-height: 100vh; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 20px; }
    .shell { max-width: 1200px; margin: 0 auto; }
  `;
}

customElements.define('app-shell', AppShell);
