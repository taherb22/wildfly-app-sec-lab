console.log('%c=== PHOENIX IAM LOADED ===', 'color: blue; font-weight: bold;');

document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const toggleFormBtn = document.getElementById('toggleFormBtn');
    const errorMessage = document.getElementById('error-message');
    const totpGroup = document.getElementById('totp-group');

    let isLoginMode = true;

    toggleFormBtn.addEventListener('click', function(e) {
        e.preventDefault();
        console.log('Toggle clicked');
        isLoginMode = !isLoginMode;

        if (isLoginMode) {
            loginForm.style.display = 'block';
            registerForm.style.display = 'none';
            document.getElementById('subtitle').textContent = 'Welcome back! Please login to continue.';
            toggleFormBtn.textContent = 'Create Account';
            document.getElementById('footer-text').textContent = "Don't have an account?";
        } else {
            loginForm.style.display = 'none';
            registerForm.style.display = 'block';
            document.getElementById('subtitle').textContent = 'Create your Phoenix IAM account';
            toggleFormBtn.textContent = 'Login';
            document.getElementById('footer-text').textContent = 'Already have an account?';
        }
    });

    loginForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        console.log('%c>>> LOGIN SUBMIT', 'color: green; font-weight: bold;');

        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        const totp = document.getElementById('totp').value.trim();

        clearError();

        try {
            const response = await fetch('/phoenix-iam/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, totp: totp || null })
            });

            const data = await response.json();
            console.log('Login response:', data);

            if (!response.ok) {
                if (data.error === 'mfa_enrollment_required') {
                    console.log('MFA enrollment required');
                    sessionStorage.setItem('enrollment_username', username);
                    sessionStorage.setItem('enrollment_password', password);
                    window.location.href = 'mfa-enroll.html';
                    return;
                }

                if (data.error === 'mfa_required') {
                    console.log('TOTP code required');
                    totpGroup.style.display = 'block';
                    document.getElementById('totp').focus();
                    showError('Please enter your authenticator code');
                    return;
                }

                throw new Error(data.error_description || data.error || 'Login failed');
            }

            console.log('Login successful');
            sessionStorage.setItem('auth_token', data.token);
            sessionStorage.setItem('username', data.username);
            showSuccess('Login successful! Redirecting...');

            setTimeout(() => {
                window.location.href = 'dashboard.html';
            }, 1000);

        } catch (error) {
            console.error('Login error:', error);
            showError(error.message);
        }
    });

    registerForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        console.log('%c>>> REGISTRATION SUBMIT', 'color: red; font-weight: bold;');

        const username = document.getElementById('reg-username').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const password = document.getElementById('reg-password').value;
        const confirmPassword = document.getElementById('reg-confirm-password').value;

        console.log('Registration data:', { username, email });

        clearError();

        if (password !== confirmPassword) {
            showError('Passwords do not match');
            return;
        }

        if (password.length < 8) {
            showError('Password must be at least 8 characters');
            return;
        }

        const submitBtn = registerForm.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.textContent = 'Registering...';

        try {
            console.log('Sending registration request...');
            const response = await fetch('/phoenix-iam/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password })
            });

            const data = await response.json();
            console.log('Registration response:', data);

            if (!response.ok) {
                throw new Error(data.error_description || data.error || 'Registration failed');
            }

            console.log('%c✅ REGISTRATION SUCCESS - STORING CREDENTIALS', 'color: green; font-weight: bold;');
            sessionStorage.setItem('enrollment_username', username);
            sessionStorage.setItem('enrollment_password', password);

            console.log('enrollment_username:', sessionStorage.getItem('enrollment_username'));
            console.log('enrollment_password:', sessionStorage.getItem('enrollment_password') ? '***SET***' : 'NOT SET');

            console.log('%c🔄 REDIRECTING TO mfa-enroll.html', 'color: purple; font-weight: bold; font-size: 14px;');
            window.location.href = 'mfa-enroll.html';

        } catch (error) {
            console.error('Registration error:', error);
            showError(error.message);
            submitBtn.disabled = false;
            submitBtn.textContent = 'Register';
        }
    });

    function showError(message) {
        errorMessage.textContent = message;
        errorMessage.style.display = 'block';
        errorMessage.style.color = '#d32f2f';
    }

    function showSuccess(message) {
        errorMessage.textContent = message;
        errorMessage.style.display = 'block';
        errorMessage.style.color = '#4CAF50';
    }

    function clearError() {
        errorMessage.textContent = '';
        errorMessage.style.display = 'none';
    }
});
