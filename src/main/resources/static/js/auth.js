async function loginUser(username, password) {
    const result = await apiFetch("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password })
    }, false);

    if (result && result.user) {
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
    try {
        const user = await apiFetch("/api/auth/me");
        if (user) {
            saveUser(user);
        }
        return user;
    } catch (error) {
        return null;
    }
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
