async function loginUser(username, password) {
    const result = await apiFetch("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password })
    }, false);

    if (result && result.user) {
        clearCsrfToken();
        saveUser(result.user);
        return result.user;
    }

    return null;
}

async function registerUser(payload) {
    return apiFetch("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(payload)
    }, false);
}

async function loadCurrentUser() {
    if (!isAuthenticated()) {
        return null;
    }
    const user = await apiFetch("/api/auth/me");
    if (user) {
        saveUser(user);
    }
    return user;
}

function redirectIfLoggedIn() {
    if (isAuthenticated()) {
        window.location.href = "dashboard.html";
    }
}

function requireAuth() {
    if (!isAuthenticated()) {
        window.location.href = "login.html";
        return false;
    }
    return true;
}
