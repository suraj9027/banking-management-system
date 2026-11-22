document.addEventListener("DOMContentLoaded", async () => {
    if (!requireAuth()) return;
    applyUserBadge();

    try {
        const transactions = await apiFetch("/api/transactions");
        const tbody = document.querySelector("#transactionsTableBody");
        if (!tbody) return;

        if (!transactions || transactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center">No transactions found.</td></tr>';
            return;
        }

        tbody.innerHTML = transactions.map(tx => `
            <tr>
                <td>${escapeHtml(tx.referenceNumber || "-")}</td>
                <td>${escapeHtml(tx.type)}</td>
                <td>${escapeHtml(tx.description || "-")}</td>
                <td>${formatCurrency(tx.amount)}</td>
                <td>${escapeHtml(tx.sourceAccountNumber || "-")}</td>
                <td>${escapeHtml(tx.targetAccountNumber || "-")}</td>
                <td>${formatDate(tx.createdAt)}</td>
            </tr>
        `).join("");
    } catch (error) {
        const alert = document.querySelector("#transactionsAlert");
        alert?.classList.remove("d-none");
        if (alert) alert.textContent = error.message;
    }
});
