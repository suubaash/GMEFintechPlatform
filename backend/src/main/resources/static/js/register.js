// T-1.1.1-20 Wire the web registration screens to the API with error states.
(function () {
    const otpForm = document.getElementById('otpForm');
    const registerForm = document.getElementById('registerForm');
    const done = document.getElementById('done');
    const errorBox = document.getElementById('error');

    let channel = 'email';
    let identifier = '';

    function showError(msg) {
        errorBox.textContent = msg;
        errorBox.classList.remove('hidden');
    }

    function clearError() {
        errorBox.classList.add('hidden');
        errorBox.textContent = '';
    }

    function show(step) {
        for (const el of [otpForm, registerForm, done]) el.classList.add('hidden');
        step.classList.remove('hidden');
    }

    async function api(path, body) {
        const res = await fetch(path, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const err = new Error(data.message || 'Request failed');
            err.code = data.code;
            throw err;
        }
        return data;
    }

    // Step 1 — request the OTP
    otpForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearError();
        channel = otpForm.querySelector('input[name=channel]:checked').value;
        identifier = document.getElementById('identifier').value.trim();
        const btn = document.getElementById('sendBtn');
        btn.disabled = true;
        try {
            const r = await api('/api/v1/registrations/otp', {identifier, channel});
            document.getElementById('sentTo').textContent = identifier;
            // Dev-only convenience: prefill the code returned while no SMS/email adapter exists.
            if (r.devCode) document.getElementById('code').value = r.devCode;
            show(registerForm);
        } catch (err) {
            showError(friendly(err));
        } finally {
            btn.disabled = false;
        }
    });

    // Step 2 — submit code + name
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById('createBtn');
        btn.disabled = true;
        try {
            const r = await api('/api/v1/registrations', {
                identifier,
                channel,
                code: document.getElementById('code').value.trim(),
                fullName: document.getElementById('fullName').value.trim(),
            });
            document.getElementById('acctStatus').textContent = r.status;
            document.getElementById('acctId').textContent = r.partyId;
            show(done);
        } catch (err) {
            showError(friendly(err));
        } finally {
            btn.disabled = false;
        }
    });

    document.getElementById('backBtn').addEventListener('click', () => {
        clearError();
        show(otpForm);
    });

    function friendly(err) {
        switch (err.code) {
            case 'otp-invalid': return 'That code is invalid or expired. Please try again.';
            case 'otp-rate-limited': return 'Too many requests. Please wait and try later.';
            case 'otp-resend-throttled': return 'Please wait a moment before requesting another code.';
            case 'duplicate-identifier': return 'An account already exists for this contact.';
            case 'validation-failed': return err.message || 'Please check your details.';
            default: return err.message || 'Something went wrong.';
        }
    }
})();
