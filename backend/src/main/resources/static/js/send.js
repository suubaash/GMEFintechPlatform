// Send-money flow: quote -> confirm -> golden path result (legs, journal, SWIFT).
(function () {
    const quoteForm = document.getElementById('quoteForm');
    const quoteResult = document.getElementById('quoteResult');
    const transferForm = document.getElementById('transferForm');
    const result = document.getElementById('result');
    const errorBox = document.getElementById('error');

    let quote = null;

    const fmtMoney = (amount, ccy) => `${Number(amount).toLocaleString()} ${ccy}`;

    function showError(msg) { errorBox.textContent = msg; errorBox.classList.remove('hidden'); }
    function clearError() { errorBox.classList.add('hidden'); errorBox.textContent = ''; }
    function show(...els) {
        for (const el of [quoteForm, quoteResult, result]) el.classList.add('hidden');
        els.forEach(e => e.classList.remove('hidden'));
    }

    async function api(path, opts) {
        const res = await fetch(path, opts);
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const err = new Error(data.message || 'Request failed');
            err.code = data.code;
            throw err;
        }
        return data;
    }
    const post = (path, body) => api(path, {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body),
    });

    async function loadCorridors() {
        const corridors = await api('/api/v1/corridors');
        const sel = document.getElementById('corridor');
        sel.innerHTML = '';
        for (const c of corridors) {
            const opt = document.createElement('option');
            opt.value = c.code;
            opt.textContent = `${c.code}  (${c.sendCurrency} → ${c.receiveCurrency})`;
            sel.appendChild(opt);
        }
    }

    quoteForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById('quoteBtn');
        btn.disabled = true;
        try {
            quote = await post('/api/v1/quotes', {
                corridor: document.getElementById('corridor').value,
                sendAmountMinor: parseInt(document.getElementById('amount').value, 10),
            });
            renderQuote(quote);
            show(quoteResult);
        } catch (err) {
            showError(friendly(err));
        } finally {
            btn.disabled = false;
        }
    });

    function renderQuote(q) {
        document.getElementById('qReceive').textContent = fmtMoney(q.receiveAmount, q.receiveCurrency);
        document.getElementById('qRate').textContent = `1 ${q.sendCurrency} = ${q.quotedRate} ${q.receiveCurrency}`;
        document.getElementById('qMid').textContent = `${q.midRate} (margin ${q.marginBps} bps)`;
        document.getElementById('qFees').textContent = fmtMoney(q.totalFees, q.sendCurrency);
        document.getElementById('qEta').textContent = `~${q.etaMinutes} min`;
        const ft = document.getElementById('feeTable');
        ft.innerHTML = '<tr><th>Fee</th><th>Layer</th><th class="num">Amount</th></tr>' +
            q.fees.map(f => `<tr><td>${f.type}</td><td><span class="tag">${f.resolvedLayer}</span></td>` +
                `<td class="num">${Number(f.amountMinor).toLocaleString()} ${f.currency}</td></tr>`).join('');
    }

    transferForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearError();
        const btn = document.getElementById('sendBtn');
        btn.disabled = true;
        try {
            const t = await post('/api/v1/transfers', {
                quoteId: quote.quoteId,
                senderName: document.getElementById('senderName').value.trim(),
                recipientName: document.getElementById('recipientName').value.trim(),
                recipientAccount: document.getElementById('recipientAccount').value.trim(),
            });
            renderResult(t);
            show(result);
        } catch (err) {
            showError(friendly(err));
        } finally {
            btn.disabled = false;
        }
    });

    function renderResult(t) {
        document.getElementById('rStatus').textContent = t.status;
        document.getElementById('rReceive').textContent = fmtMoney(t.receiveAmount, t.receiveCurrency);
        document.getElementById('rId').textContent = t.transferId;

        document.getElementById('legTable').innerHTML =
            '<tr><th>Leg</th><th>Status</th><th class="num">Amount</th></tr>' +
            t.legs.map(l => `<tr><td>${l.kind}</td><td><span class="tag ok">${l.status}</span></td>` +
                `<td class="num">${Number(l.amount).toLocaleString()} ${l.currency}</td></tr>`).join('');

        let rows = '<tr><th>Movement</th><th>Account</th><th>Dr/Cr</th><th class="num">Amount</th></tr>';
        for (const jv of t.journal) {
            jv.postings.forEach((p, i) => {
                const dc = p.direction === 'DEBIT' ? '<span class="dr">DR</span>' : '<span class="cr">CR</span>';
                rows += `<tr><td>${i === 0 ? jv.movementType : ''}</td><td>${p.accountCode}</td>` +
                    `<td>${dc}</td><td class="num">${Number(p.amountMinor).toLocaleString()} ${jv.currency}</td></tr>`;
            });
        }
        document.getElementById('journalTable').innerHTML = rows;

        document.getElementById('swiftBox').textContent = t.swift.length ? t.swift[0].finText : '(none)';
    }

    document.getElementById('reQuoteBtn').addEventListener('click', () => { clearError(); show(quoteForm); });
    document.getElementById('againBtn').addEventListener('click', () => { clearError(); show(quoteForm); });

    function friendly(err) {
        switch (err.code) {
            case 'corridor-not-supported': return 'That corridor is not supported.';
            case 'rate-source-unavailable': return 'FX rate temporarily unavailable. Try again.';
            case 'fee-config-missing': return 'No fee configuration for this corridor.';
            case 'quote-expired': return 'The quote expired. Please get a new quote.';
            case 'validation-failed': return err.message || 'Please check your details.';
            default: return err.message || 'Something went wrong.';
        }
    }

    show(quoteForm);
    loadCorridors().catch(() => showError('Could not load corridors.'));
})();
