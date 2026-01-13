import { LitElement, html, css } from '../lib/lit.js';
import { enqueueOfflineRequest } from '../services/offline-queue.js';
import { getToken, clearToken } from '../utils/storage.js';

export class UserView extends LitElement {
  static properties = {
    activeTab: { type: String },
    basket: { type: Array },
    user: { type: Object }
  };

  constructor() {
    super();
    this.activeTab = 'profile';
    this.basket = JSON.parse(localStorage.getItem('basket') || '[]');
    
    // Get user info from sessionStorage
    const userStr = sessionStorage.getItem('currentUser');
    this.user = userStr ? JSON.parse(userStr) : { username: 'Guest', email: 'guest@phoenix.com' };
  }

  logout() {
    clearToken();
    sessionStorage.removeItem('currentUser');
    localStorage.removeItem('basket');
    
    this.dispatchEvent(new CustomEvent('navigate', {
      detail: 'login',
      bubbles: true,
      composed: true
    }));
  }

  switchTab(tab) {
    this.activeTab = tab;
  }

  addToBasket(recipe) {
    this.basket = [...this.basket, recipe];
    localStorage.setItem('basket', JSON.stringify(this.basket));
  }

  removeFromBasket(index) {
    this.basket = this.basket.filter((_, i) => i !== index);
    localStorage.setItem('basket', JSON.stringify(this.basket));
  }

  clearBasket() {
    if (confirm('Clear all items from basket?')) {
      this.basket = [];
      localStorage.removeItem('basket');
    }
  }

  getTotalPrice() {
    return this.basket.reduce((sum, item) => sum + item.price, 0).toFixed(2);
  }

  renderProfile() {
    return html`
      <div class="profile-card">
        <div class="profile-header">
          <div class="avatar">${this.user.username[0].toUpperCase()}</div>
          <div>
            <h3>${this.user.username}</h3>
            <p>${this.user.email}</p>
          </div>
        </div>

        <div class="profile-info">
          <div class="info-row">
            <span class="label">Username:</span>
            <span class="value">${this.user.username}</span>
          </div>
          <div class="info-row">
            <span class="label">Email:</span>
            <span class="value">${this.user.email}</span>
          </div>
          <div class="info-row">
            <span class="label">Status:</span>
            <span class="value status-active">🟢 Active</span>
          </div>
          <div class="info-row">
            <span class="label">Basket Items:</span>
            <span class="value">${this.basket.length}</span>
          </div>
        </div>
      </div>
    `;
  }

  renderRecipes() {
    const recipes = [
      {
        name: 'Pasta Carbonara',
        description: 'An Italian dish, easy to prepare and delicious to eat. Creamy sauce with bacon and parmesan.',
        price: 12.99,
        emoji: '🍝'
      },
      {
        name: 'Margherita Pizza',
        description: 'Classic Italian pizza with fresh mozzarella, tomatoes, and basil. Simply irresistible!',
        price: 15.99,
        emoji: '🍕'
      },
      {
        name: 'Chicken Tikka Masala',
        description: 'Aromatic Indian curry with tender chicken in a creamy tomato sauce. Full of flavor!',
        price: 14.99,
        emoji: '🍛'
      },
      {
        name: 'Caesar Salad',
        description: 'Fresh romaine lettuce with parmesan, croutons and Caesar dressing. Light and healthy!',
        price: 9.99,
        emoji: '🥗'
      },
      {
        name: 'Beef Burger',
        description: 'Juicy beef patty with cheese, lettuce, tomato and special sauce. American classic!',
        price: 13.99,
        emoji: '🍔'
      },
      {
        name: 'Sushi Platter',
        description: 'Assorted fresh sushi rolls with wasabi and soy sauce. Japanese delicacy!',
        price: 18.99,
        emoji: '🍣'
      }
    ];

    return html`
      <div class="recipes-grid">
        ${recipes.map(recipe => html`
          <div class="recipe-card">
            <div class="recipe-emoji">${recipe.emoji}</div>
            <h3>${recipe.name}</h3>
            <p class="recipe-description">${recipe.description}</p>
            <div class="recipe-footer">
              <span class="price">$${recipe.price}</span>
              <button 
                class="btn-add" 
                @click=${() => this.addToBasket(recipe)}
              >
                Add to Basket
              </button>
            </div>
          </div>
        `)}
      </div>
    `;
  }

  renderBasket() {
    if (this.basket.length === 0) {
      return html`
        <div class="empty-basket">
          <p>🛒 Your basket is empty</p>
          <p class="subtitle">Add some delicious recipes!</p>
          <button class="btn-primary" @click=${() => this.switchTab('recipes')}>
            Browse Recipes
          </button>
        </div>
      `;
    }

    return html`
      <div class="basket-container">
        <div class="basket-header">
          <h3>Your Basket (${this.basket.length} items)</h3>
          <button class="btn-clear" @click=${this.clearBasket}>Clear All</button>
        </div>

        <div class="basket-items">
          ${this.basket.map((item, index) => html`
            <div class="basket-item">
              <div class="item-info">
                <span class="item-emoji">${item.emoji}</span>
                <div>
                  <h4>${item.name}</h4>
                  <p class="item-price">$${item.price}</p>
                </div>
              </div>
              <button 
                class="btn-remove" 
                @click=${() => this.removeFromBasket(index)}
              >
                ✕
              </button>
            </div>
          `)}
        </div>

        <div class="basket-total">
          <div class="total-row">
            <span>Subtotal:</span>
            <span>$${this.getTotalPrice()}</span>
          </div>
          <div class="total-row">
            <span>Tax (10%):</span>
            <span>$${(this.getTotalPrice() * 0.1).toFixed(2)}</span>
          </div>
          <div class="total-row total">
            <span>Total:</span>
            <span>$${(this.getTotalPrice() * 1.1).toFixed(2)}</span>
          </div>
          <button class="btn-checkout">Proceed to Checkout</button>
        </div>
      </div>
    `;
  }

  render() {
    return html`
      <div class="dashboard">
        <header class="dashboard-header">
          <h1>🍽️ Phoenix Food Hub</h1>
          <button class="btn-logout" @click=${this.logout}>Logout</button>
        </header>

        <nav class="tabs">
          <button 
            class="tab ${this.activeTab === 'profile' ? 'active' : ''}"
            @click=${() => this.switchTab('profile')}
          >
            👤 Profile
          </button>
          <button 
            class="tab ${this.activeTab === 'recipes' ? 'active' : ''}"
            @click=${() => this.switchTab('recipes')}
          >
            📖 Recipes
          </button>
          <button 
            class="tab ${this.activeTab === 'basket' ? 'active' : ''}"
            @click=${() => this.switchTab('basket')}
          >
            🛒 Basket ${this.basket.length > 0 ? `(${this.basket.length})` : ''}
          </button>
        </nav>

        <main class="content">
          ${this.activeTab === 'profile' ? this.renderProfile() : ''}
          ${this.activeTab === 'recipes' ? this.renderRecipes() : ''}
          ${this.activeTab === 'basket' ? this.renderBasket() : ''}
        </main>
      </div>
    `;
  }

  static styles = css`
    :host {
      display: block;
    }

    .dashboard {
      max-width: 1200px;
      margin: 0 auto;
      padding: 20px;
    }

    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 15px;
      color: white;
      margin-bottom: 30px;
    }

    .dashboard-header h1 {
      margin: 0;
      font-size: 1.8rem;
    }

    .btn-logout {
      padding: 10px 20px;
      background: white;
      color: #667eea;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s;
    }

    .btn-logout:hover {
      transform: translateY(-2px);
    }

    .tabs {
      display: flex;
      gap: 10px;
      margin-bottom: 30px;
      border-bottom: 2px solid #e0e0e0;
    }

    .tab {
      padding: 15px 30px;
      background: none;
      border: none;
      border-bottom: 3px solid transparent;
      cursor: pointer;
      font-size: 1rem;
      font-weight: 600;
      color: #666;
      transition: all 0.3s;
    }

    .tab:hover {
      color: #667eea;
    }

    .tab.active {
      color: #667eea;
      border-bottom-color: #667eea;
    }

    .content {
      min-height: 400px;
    }

    /* Profile Styles */
    .profile-card {
      background: white;
      padding: 30px;
      border-radius: 15px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
      max-width: 600px;
    }

    .profile-header {
      display: flex;
      align-items: center;
      gap: 20px;
      padding-bottom: 20px;
      border-bottom: 2px solid #f0f0f0;
      margin-bottom: 20px;
    }

    .avatar {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      font-weight: bold;
    }

    .profile-header h3 {
      margin: 0;
      color: #333;
    }

    .profile-header p {
      margin: 5px 0 0 0;
      color: #666;
    }

    .profile-info {
      display: flex;
      flex-direction: column;
      gap: 15px;
    }

    .info-row {
      display: flex;
      justify-content: space-between;
      padding: 12px;
      background: #f8f9fa;
      border-radius: 8px;
    }

    .label {
      font-weight: 600;
      color: #555;
    }

    .value {
      color: #333;
    }

    .status-active {
      color: #28a745;
    }

    /* Recipes Grid */
    .recipes-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 20px;
    }

    .recipe-card {
      background: white;
      padding: 25px;
      border-radius: 15px;
      box-shadow: 0 4px 15px rgba(0,0,0,0.1);
      transition: transform 0.3s;
    }

    .recipe-card:hover {
      transform: translateY(-5px);
    }

    .recipe-emoji {
      font-size: 4rem;
      text-align: center;
      margin-bottom: 15px;
    }

    .recipe-card h3 {
      color: #333;
      margin: 0 0 10px 0;
      font-size: 1.3rem;
    }

    .recipe-description {
      color: #666;
      line-height: 1.6;
      margin-bottom: 20px;
      min-height: 60px;
    }

    .recipe-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .price {
      font-size: 1.5rem;
      font-weight: bold;
      color: #667eea;
    }

    .btn-add {
      padding: 10px 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s;
    }

    .btn-add:hover {
      transform: scale(1.05);
    }

    /* Basket Styles */
    .basket-container {
      background: white;
      padding: 30px;
      border-radius: 15px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
      max-width: 800px;
    }

    .basket-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding-bottom: 20px;
      border-bottom: 2px solid #f0f0f0;
    }

    .basket-header h3 {
      margin: 0;
      color: #333;
    }

    .btn-clear {
      padding: 8px 16px;
      background: #dc3545;
      color: white;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-weight: 600;
    }

    .basket-items {
      display: flex;
      flex-direction: column;
      gap: 15px;
      margin-bottom: 30px;
    }

    .basket-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 15px;
      background: #f8f9fa;
      border-radius: 10px;
    }

    .item-info {
      display: flex;
      align-items: center;
      gap: 15px;
    }

    .item-emoji {
      font-size: 2.5rem;
    }

    .basket-item h4 {
      margin: 0;
      color: #333;
    }

    .item-price {
      margin: 5px 0 0 0;
      color: #667eea;
      font-weight: 600;
    }

    .btn-remove {
      width: 30px;
      height: 30px;
      border-radius: 50%;
      background: #dc3545;
      color: white;
      border: none;
      cursor: pointer;
      font-size: 1.2rem;
      transition: transform 0.2s;
    }

    .btn-remove:hover {
      transform: scale(1.1);
    }

    .basket-total {
      padding-top: 20px;
      border-top: 2px solid #e0e0e0;
    }

    .total-row {
      display: flex;
      justify-content: space-between;
      padding: 10px 0;
      font-size: 1.1rem;
    }

    .total-row.total {
      font-size: 1.5rem;
      font-weight: bold;
      color: #667eea;
      padding-top: 15px;
      border-top: 2px solid #e0e0e0;
      margin-top: 10px;
    }

    .btn-checkout {
      width: 100%;
      padding: 15px;
      margin-top: 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 10px;
      font-size: 1.1rem;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s;
    }

    .btn-checkout:hover {
      transform: translateY(-2px);
    }

    .empty-basket {
      text-align: center;
      padding: 60px 20px;
      background: white;
      border-radius: 15px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.1);
    }

    .empty-basket p {
      font-size: 1.5rem;
      color: #666;
      margin: 10px 0;
    }

    .empty-basket .subtitle {
      font-size: 1rem;
      color: #999;
      margin-bottom: 30px;
    }

    .btn-primary {
      padding: 15px 30px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 10px;
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: transform 0.2s;
    }

    .btn-primary:hover {
      transform: translateY(-2px);
    }
  `;
}

customElements.define('user-view', UserView);
