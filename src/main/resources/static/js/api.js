const API_BASE = "/";

async function apiFetch(path, options = {}, requireAuth = true) {
    const headers = new Headers(options.headers || {});
    if (!(options.body instanceof FormData) && !headers.has("Content-Type") && options.body) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(API_BASE + path.replace(/^\//, ""), {
        ...options,
        headers,
        credentials: "include"
    });

    if (response.status === 401) {
        localStorage.removeItem("bankUser");
        if (window.location.pathname.endsWith("login.html") === false) {
            window.location.href = "login.html";
        }
        return null;
    }

    if (!response.ok) {
        let errorMessage = "Request failed";
        try {
            const data = await response.json();
            errorMessage = data.message || errorMessage;
        } catch (e) {
            // ignore JSON parse failures
        }
        throw new Error(errorMessage);
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
    return raw ? JSON.parse(raw) : null;
}

function isAuthenticated() {
    return Boolean(getStoredUser());
}

async function logoutUser() {
    try {
        await apiFetch("/api/auth/logout", { method: "POST" }, false);
    } catch (error) {
        // logout may fail; still clear local state
    }
    localStorage.removeItem("bankUser");
    window.location.href = "login.html";
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
