async function refreshAccounts() {
    try {
        const accounts = await apiFetch("/api/accounts");
        const tbody = document.querySelector("#accountTableBody");
        if (!tbody) return;

        if (!accounts || accounts.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">No accounts found yet.</td></tr>';
            return;
        }

        tbody.innerHTML = accounts.map(account => `
            <tr>
                <td>${account.accountNumber}</td>
                <td>${account.accountType}</td>
                <td>${account.status}</td>
                <td>${formatCurrency(account.balance)}</td>
                <td>${formatDate(account.createdAt)}</td>
                <td>
                    <a class="btn btn-sm btn-primary" href="account-details.html?id=${account.id}">View</a>
                </td>
            </tr>
        `).join("");
    } catch (error) {
        document.querySelector("#accountsAlert")?.classList.remove("d-none");
        document.querySelector("#accountsAlert").textContent = error.message;
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    if (!requireAuth()) return;
    applyUserBadge();
    await refreshAccounts();

    document.getElementById('createAccountForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        const alertBox = document.getElementById('createAccountAlert');
        const successBox = document.getElementById('createAccountSuccess');
        try {
            await apiFetch('/api/accounts', {
                method: 'POST',
                body: JSON.stringify({
                    accountType: document.getElementById('accountType').value,
                    balance: Number(document.getElementById('initialBalance').value || 0)
                })
            });
            successBox.classList.remove('d-none');
            successBox.textContent = 'Account created successfully.';
            alertBox.classList.add('d-none');
            document.getElementById('createAccountForm').reset();
            await refreshAccounts();
        } catch (error) {
            alertBox.classList.remove('d-none');
            alertBox.textContent = error.message;
            successBox.classList.add('d-none');
        }
    });
});
