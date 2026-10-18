document.addEventListener("DOMContentLoaded", async () => {
    if (!requireAuth()) return;
    applyUserBadge();

    try {
        const transactions = await apiFetch("/api/transactions");
        const tbody = document.querySelector("#transactionsTableBody");
        if (!tbody) return;

        if (!transactions || transactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">No transactions found.</td></tr>';
            return;
        }

        tbody.innerHTML = transactions.map(tx => `
            <tr>
                <td>${tx.referenceNumber || "-"}</td>
                <td>${tx.type}</td>
                <td>${tx.description || "-"}</td>
                <td>${formatCurrency(tx.amount)}</td>
                <td>${tx.sourceAccountNumber || "-"}</td>
                <td>${tx.targetAccountNumber || "-"}</td>
                <td>${formatDate(tx.createdAt)}</td>
            </tr>
        `).join("");
    } catch (error) {
        document.querySelector("#transactionsAlert")?.classList.remove("d-none");
        document.querySelector("#transactionsAlert").textContent = error.message;
    }
});
