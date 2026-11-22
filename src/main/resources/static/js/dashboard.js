document.addEventListener("DOMContentLoaded", async () => {
    if (!requireAuth()) return;
    applyUserBadge();

    const user = getStoredUser();
    const firstName = user?.firstName || "there";
    const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(" ");
    const initials = [user?.firstName, user?.lastName]
        .filter(Boolean)
        .map(name => name.charAt(0))
        .join("")
        .toUpperCase();
    document.querySelector("#welcomeHeading").textContent = `Welcome back, ${firstName}`;
    document.querySelector("#userInitials").textContent = initials || "U";
    document.querySelector("#userBadge").textContent = fullName || "My account";
    document.querySelector("#dashboardDate").textContent = new Intl.DateTimeFormat(undefined, {
        weekday: "long",
        month: "long",
        day: "numeric"
    }).format(new Date());

    try {
        const [summary, monthlySummary, recentTransactions] = await Promise.all([
            apiFetch("/api/dashboard/summary"),
            apiFetch("/api/dashboard/monthly-summary"),
            apiFetch("/api/dashboard/recent-transactions")
        ]);

        if (!summary || !monthlySummary || !recentTransactions) return;
        document.querySelector("#totalBalance").textContent = formatCurrency(summary.totalBalance || 0);
        document.querySelector("#activeAccounts").textContent = summary.activeAccounts || 0;
        document.querySelector("#totalDeposits").textContent = formatCurrency(summary.totalDeposits || 0);
        document.querySelector("#totalWithdrawals").textContent = formatCurrency(summary.totalWithdrawals || 0);

        const labels = monthlySummary.map(item => item.month);
        const income = monthlySummary.map(item => Number(item.income || 0));
        const expenses = monthlySummary.map(item => Number(item.expense || 0));

        const ctx = document.getElementById("monthlyChart");
        if (ctx && window.Chart) {
            new Chart(ctx, {
                type: "bar",
                data: {
                    labels,
                    datasets: [
                        {
                            label: "Money in",
                            data: income,
                            backgroundColor: "#46c3a0",
                            borderRadius: 6,
                            maxBarThickness: 24
                        },
                        {
                            label: "Money out",
                            data: expenses,
                            backgroundColor: "#9cb8f4",
                            borderRadius: 6,
                            maxBarThickness: 24
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: {
                        mode: "index",
                        intersect: false
                    },
                    plugins: {
                        legend: {
                            position: "bottom",
                            align: "start",
                            labels: {
                                usePointStyle: true,
                                pointStyle: "circle",
                                boxWidth: 7,
                                boxHeight: 7,
                                padding: 18,
                                color: "#75839a",
                                font: { size: 11 }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                label: context => `${context.dataset.label}: ${formatCurrency(context.raw)}`
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: { display: false },
                            border: { display: false },
                            ticks: { color: "#8b97aa", font: { size: 10 } }
                        },
                        y: {
                            beginAtZero: true,
                            border: { display: false, dash: [4, 4] },
                            grid: { color: "#eef1f6", drawTicks: false },
                            ticks: {
                                color: "#8b97aa",
                                padding: 9,
                                font: { size: 10 },
                                callback: value => formatCurrency(value)
                            }
                        }
                    }
                }
            });
        }

        const tableBody = document.querySelector("#recentTransactionsBody");
        if (tableBody) {
            tableBody.innerHTML = recentTransactions.length ? recentTransactions.map(tx => `
                <tr>
                    <td>
                        <div class="transaction-name">
                            <span class="transaction-type-icon ${tx.direction === "INCOMING" ? "incoming" : "outgoing"}">${tx.direction === "INCOMING" ? "↓" : "↑"}</span>
                            <span>
                                <span class="transaction-type-label">${escapeHtml(tx.type || "Transaction")}</span>
                                <span class="transaction-description">${escapeHtml(tx.description || "-")}</span>
                            </span>
                        </div>
                    </td>
                    <td>${escapeHtml(tx.referenceNumber || "-")}</td>
                    <td>${escapeHtml(formatDate(tx.createdAt))}</td>
                    <td class="text-end transaction-amount ${tx.direction === "INCOMING" ? "incoming" : "outgoing"}">${tx.direction === "INCOMING" ? "+" : "-"}${formatCurrency(tx.amount)}</td>
                </tr>
            `).join("") : '<tr><td colspan="4" class="table-loading">No transactions yet. Your activity will appear here.</td></tr>';
        }
    } catch (error) {
        document.querySelector("#dashboardAlert")?.classList.remove("d-none");
        document.querySelector("#dashboardAlert").textContent = error.message;
    }
});
