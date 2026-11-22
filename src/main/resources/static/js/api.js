const API_BASE = "/";
const safeMethods = new Set(["GET", "HEAD", "OPTIONS"]);
let csrfToken;

async function getCsrfToken() {
    if (csrfToken) {
        return csrfToken;
    }

    const response = await fetch(`${API_BASE}api/auth/csrf`, { credentials: "include" });
    if (!response.ok) {
        throw new Error("Unable to initialize request security. Refresh the page and try again.");
    }
    const data = await response.json();
    if (!data.token) {
        throw new Error("The server did not provide a request security token.");
    }
    csrfToken = data.token;
    return csrfToken;
}

async function getErrorMessage(response) {
    const body = await response.text();
    if (!body) {
        return response.statusText || "Request failed";
    }
    try {
        const data = JSON.parse(body);
        return data.message || response.statusText || "Request failed";
    } catch {
        return body;
    }
}

async function apiFetch(path, options = {}, requireAuth = true) {
    const headers = new Headers(options.headers || {});
    const method = (options.method || "GET").toUpperCase();
    if (!(options.body instanceof FormData) && !headers.has("Content-Type") && options.body) {
        headers.set("Content-Type", "application/json");
    }
    if (!safeMethods.has(method)) {
        headers.set("X-CSRF-TOKEN", await getCsrfToken());
    }

    const response = await fetch(API_BASE + path.replace(/^\//, ""), {
        ...options,
        method,
        headers,
        credentials: "include"
    });

    if (response.status === 401) {
        localStorage.removeItem("bankUser");
        if (requireAuth && !window.location.pathname.endsWith("login.html")) {
            window.location.href = "login.html";
        }
        throw new Error(await getErrorMessage(response));
    }

    if (!response.ok) {
        throw new Error(await getErrorMessage(response));
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return response.json();
    }
    return null;
}

function saveUser(user) {
    localStorage.setItem("bankUser", JSON.stringify(user));
}

function getStoredUser() {
    const raw = localStorage.getItem("bankUser");
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch {
        localStorage.removeItem("bankUser");
        return null;
    }
}

function isAuthenticated() {
    return Boolean(getStoredUser());
}

async function logoutUser() {
    try {
        await apiFetch("/api/auth/logout", { method: "POST" }, false);
    } catch (error) {
        window.alert(error.message);
        return;
    }
    localStorage.removeItem("bankUser");
    window.location.href = "login.html";
}

function clearCsrfToken() {
    csrfToken = undefined;
}

async function populateOwnedAccountSelect(selectId) {
    const select = document.getElementById(selectId);
    if (!select) {
        return;
    }
    const accounts = await apiFetch("/api/accounts");
    select.replaceChildren(new Option("Select an active account", ""));
    for (const account of accounts || []) {
        if (account.status === "ACTIVE") {
            select.add(new Option(`${account.accountNumber} · ${account.accountType}`, account.id));
        }
    }
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

function formatCurrency(value) {
    if (value === null || value === undefined || value === "") {
        return "$0.00";
    }
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(Number(value));
}

function formatDate(dateValue) {
    if (!dateValue) {
        return "-";
    }
    const date = new Date(dateValue);
    return date.toLocaleString();
}

function applyUserBadge() {
    const user = getStoredUser();
    const element = document.querySelector("#userBadge");
    if (element && user) {
        element.textContent = `${user.firstName} ${user.lastName}`;
    }
}
