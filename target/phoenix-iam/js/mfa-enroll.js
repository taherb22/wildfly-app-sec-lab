console.log('MFA Enrollment script loaded');

const MFAEnrollment = {
    username: null,
    password: null,
    totpSecret: null,

    init() {
        console.log('Initializing MFA enrollment...');
        this.username = sessionStorage.getItem('enrollment_username');
        this.password = sessionStorage.getItem('enrollment_password');

        console.log('Username from session:', this.username);

        if (!this.username || !this.password) {
            console.error('No credentials found in session');
            this.showError('Invalid session. Please register again.');
            window.setTimeout(() => window.location.href = 'index.html', 2000);
            return;
        }

        this.enrollMFA();
        this.setupEventListeners();
    },

    setupEventListeners() {
        const copySecretBtn = document.getElementById('copy-secret');
        const btnNext = document.getElementById('btn-next');
        const btnBack = document.getElementById('btn-back');
        const verifyForm = document.getElementById('verify-form');
        const btnContinue = document.getElementById('btn-continue');

        if (copySecretBtn) copySecretBtn.addEventListener('click', (e) => { e.preventDefault(); this.copySecret(); });
        if (btnNext) btnNext.addEventListener('click', (e) => { e.preventDefault(); this.showVerifyStep(); });
        if (btnBack) btnBack.addEventListener('click', (e) => { e.preventDefault(); this.showQRStep(); });
        if (verifyForm) verifyForm.addEventListener('submit', (e) => this.verifyCode(e));
        if (btnContinue) btnContinue.addEventListener('click', (e) => { e.preventDefault(); this.continueToLogin(); });
    },

    async enrollMFA() {
        this.showLoading(true);

        try {
            console.log('Enrolling MFA for user:', this.username);
            const response = await fetch('/phoenix-iam/api/auth/mfa/enroll', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: this.username,
                    password: this.password
                })
            });

            const data = await response.json();
            console.log('MFA enrollment response:', data);

            if (!response.ok) {
                throw new Error(data.error_description || 'MFA enrollment failed');
            }

            this.totpSecret = data.secret;
            this.displayQRCode(data.qr_data_uri || data.qrDataUri);
            this.displaySecret(data.secret);

        } catch (error) {
            console.error('MFA enrollment error:', error);
            this.showError(error.message);
        } finally {
            this.showLoading(false);
        }
    },

    displayQRCode(qrDataUri) {
        const qrContainer = document.getElementById('qr-container');
        if (!qrContainer) return;

        if (qrDataUri) {
            if (qrDataUri.startsWith('data:image/svg+xml')) {
                const img = document.createElement('img');
                img.src = qrDataUri;
                img.alt = 'TOTP QR Code';
                img.style.maxWidth = '250px';
                qrContainer.innerHTML = '';
                qrContainer.appendChild(img);
            } else {
                qrContainer.innerHTML = qrDataUri;
            }
        }
    },

    displaySecret(secret) {
        const secretElement = document.getElementById('totp-secret');
        if (secretElement) {
            secretElement.textContent = this.formatSecret(secret);
        }
    },

    formatSecret(secret) {
        const match = secret.match(/.{1,4}/g);
        return match ? match.join(' ') : secret;
    },

    copySecret() {
        if (!this.totpSecret) return;
        navigator.clipboard.writeText(this.totpSecret).then(() => {
            const btn = document.getElementById('copy-secret');
            if (!btn) return;
            const originalText = btn.textContent;
            btn.textContent = 'Copied!';
            window.setTimeout(() => { btn.textContent = originalText; }, 2000);
        }).catch(() => this.showError('Failed to copy secret'));
    },

    showQRStep() {
        this.updateStepVisibility('step-qr');
    },

    showVerifyStep() {
        this.updateStepVisibility('step-verify');
        const totpCodeInput = document.getElementById('totp-code');
        if (totpCodeInput) totpCodeInput.focus();
    },

    showSuccessStep() {
        this.updateStepVisibility('step-success');
    },

    updateStepVisibility(activeStepId) {
        const steps = document.querySelectorAll('.enrollment-step');
        steps.forEach((step) => step.classList.remove('active'));
        const activeStep = document.getElementById(activeStepId);
        if (activeStep) activeStep.classList.add('active');
    },

    async verifyCode(event) {
        event.preventDefault();
        
        const totpCodeInput = document.getElementById('totp-code');
        const code = totpCodeInput ? totpCodeInput.value.trim() : '';
        const errorEl = document.getElementById('verify-error');

        if (errorEl) {
            errorEl.textContent = '';
            errorEl.style.display = 'none';
        }

        if (!/^\d{6}$/.test(code)) {
            if (errorEl) {
                errorEl.textContent = 'Please enter a valid 6-digit code';
                errorEl.style.display = 'block';
            }
            return;
        }

        this.showLoading(true);

        try {
            console.log('Verifying TOTP code...');
            const response = await fetch('/phoenix-iam/api/auth/mfa/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: this.username,
                    password: this.password,
                    code: code
                })
            });

            const data = await response.json();
            console.log('Verification response:', data);

            if (!response.ok) {
                throw new Error(data.error_description || 'Verification failed');
            }

            sessionStorage.removeItem('enrollment_password');
            this.showSuccessStep();

        } catch (error) {
            console.error('Verification error:', error);
            if (errorEl) {
                errorEl.textContent = error.message;
                errorEl.style.display = 'block';
            }
        } finally {
            this.showLoading(false);
        }
    },

    continueToLogin() {
        sessionStorage.removeItem('enrollment_username');
        window.location.href = 'index.html';
    },

    showLoading(show) {
        const loadingEl = document.getElementById('loading');
        const stepsEl = document.getElementById('enrollment-steps');

        if (loadingEl) loadingEl.style.display = show ? 'block' : 'none';
        if (stepsEl) stepsEl.style.display = show ? 'none' : 'block';
    },

    showError(message) {
        const errorEl = document.getElementById('error-message');
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM Content Loaded, initializing MFA enrollment');
    MFAEnrollment.init();
});
