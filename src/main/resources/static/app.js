"use strict";

const state = { csrf: null, user: null, folders: [], devices: [] };
const $ = (selector) => document.querySelector(selector);
const authView = $("#auth-view");
const dashboardView = $("#dashboard-view");

async function loadCsrf() {
    const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    if (!response.ok) throw new Error("Could not initialize browser security");
    state.csrf = await response.json();
}

async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");
    if (options.json !== undefined) {
        headers.set("Content-Type", "application/json");
        options.body = JSON.stringify(options.json);
    }
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        if (!state.csrf) await loadCsrf();
        headers.set(state.csrf.headerName, state.csrf.token);
    }
    const response = await fetch(path, {
        ...options,
        method,
        headers,
        credentials: "same-origin"
    });
    if (response.status === 401) throw new Error("Please sign in first");
    if (!response.ok) {
        let message = `Request failed (${response.status})`;
        try {
            const error = await response.json();
            message = error.message || message;
        } catch (_) { /* response had no JSON error body */ }
        throw new Error(message);
    }
    if (response.status === 204) return null;
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

function showAuth() {
    authView.hidden = false;
    dashboardView.hidden = true;
    state.user = null;
}

function showDashboard() {
    authView.hidden = true;
    dashboardView.hidden = false;
    $("#user-email").textContent = state.user.email;
}

async function initialize() {
    try {
        await loadCsrf();
        state.user = await api("/api/auth/me");
        showDashboard();
        await refreshDashboard();
    } catch (_) {
        showAuth();
    }
}

$("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = new URLSearchParams({ email: form.get("email"), password: form.get("password") });
    const message = $("#login-message");
    message.textContent = "Signing in…";
    try {
        await api("/api/auth/login", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body
        });
        await loadCsrf();
        state.user = await api("/api/auth/me");
        message.textContent = "";
        showDashboard();
        await refreshDashboard();
    } catch (error) {
        message.textContent = error.message;
    }
});

$("#register-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const message = $("#register-message");
    message.textContent = "Creating account…";
    try {
        await api("/api/auth/register", {
            method: "POST",
            json: { email: form.get("email"), password: form.get("password") }
        });
        message.textContent = "Account created. You can sign in now.";
        $("#login-form input[name=email]").value = form.get("email");
        event.currentTarget.reset();
    } catch (error) {
        message.textContent = error.message;
    }
});

$("#logout-button").addEventListener("click", async () => {
    try { await api("/api/auth/logout", { method: "POST" }); } finally {
        await loadCsrf();
        showAuth();
    }
});

$("#refresh-button").addEventListener("click", refreshDashboard);

async function refreshDashboard() {
    try {
        const [folders, devices] = await Promise.all([
            api("/api/folders"),
            api("/api/devices")
        ]);
        state.folders = folders;
        state.devices = devices;
        renderSummary();
        renderFolderOptions();
        renderFolders();
        renderDevices();
    } catch (error) {
        toast(error.message);
    }
}

function renderSummary() {
    $("#folder-count").textContent = state.folders.length;
    $("#device-count").textContent = state.devices.filter(device => !device.revokedAt).length;
    $("#current-count").textContent = state.devices.filter(device => device.syncStatus === "UP_TO_DATE").length;
}

function folderPath(folderId, visiting = new Set()) {
    const folder = state.folders.find(item => item.id === folderId);
    if (!folder) return "Unknown folder";
    if (visiting.has(folderId)) return "Invalid folder cycle";
    visiting.add(folderId);
    return folder.parentId ? `${folderPath(folder.parentId, visiting)} / ${folder.name}` : folder.name;
}

function sortedFolders() {
    return [...state.folders].sort((a, b) => folderPath(a.id).localeCompare(folderPath(b.id)));
}

function renderFolderOptions() {
    const setup = $("#setup-folder");
    setup.replaceChildren();
    if (state.folders.length === 0) {
        setup.append(new Option("Create a folder first", ""));
        setup.disabled = true;
        return;
    }
    setup.disabled = false;
    for (const folder of sortedFolders()) setup.append(new Option(folderPath(folder.id), folder.id));
}

function renderFolders() {
    const list = $("#folder-list");
    list.replaceChildren();
    if (state.folders.length === 0) {
        list.append(emptyMessage("No remote folders yet. Create one above."));
        return;
    }
    for (const folder of sortedFolders()) {
        const devices = state.devices.filter(device => device.selectedFolderId === folder.id && !device.revokedAt);
        const row = element("div", "folder-row");
        const name = element("div", "folder-name");
        name.append(element("span", "folder-icon"), textElement("span", folderPath(folder.id), "path"));
        const chips = element("div", "device-chips");
        if (devices.length === 0) chips.append(textElement("span", "not synced", "chip"));
        for (const device of devices) {
            const chip = textElement("span", device.name, `status ${statusClass(device.syncStatus)}`);
            chip.title = formatStatus(device);
            chips.append(chip);
        }
        row.append(name, chips);
        list.append(row);
    }
}

function renderDevices() {
    const list = $("#device-list");
    list.replaceChildren();
    if (state.devices.length === 0) {
        list.append(emptyMessage("No devices registered yet."));
        return;
    }
    for (const device of state.devices) {
        const row = element("div", "device-row");
        const details = element("div", "device-details");
        const title = textElement("strong", device.name);
        const status = textElement("span", prettyStatus(device.syncStatus), `status ${statusClass(device.syncStatus)}`);
        const top = element("div", "folder-name");
        top.append(title, status);
        const folder = device.selectedFolderId ? folderPath(device.selectedFolderId) : "No folder selected";
        const lastSync = device.lastSyncAt ? new Date(device.lastSyncAt).toLocaleString() : "never";
        details.append(top, textElement("span", `${folder} · last successful sync: ${lastSync}`, "device-meta"));

        const actions = element("div", "device-actions");
        if (!device.revokedAt) {
            const select = document.createElement("select");
            select.setAttribute("aria-label", `Folder synchronized by ${device.name}`);
            for (const folderItem of sortedFolders()) select.append(new Option(folderPath(folderItem.id), folderItem.id));
            if (device.selectedFolderId) select.value = device.selectedFolderId;
            const save = textElement("button", "Save folder");
            save.type = "button";
            save.addEventListener("click", () => updateDeviceFolder(device.id, select.value));
            actions.append(select, save);
            const revoke = textElement("button", "Revoke", "danger-button");
            revoke.type = "button";
            revoke.addEventListener("click", () => revokeDevice(device));
            actions.append(document.createElement("span"), revoke);
        }
        row.append(details, actions);
        list.append(row);
    }
}

$("#folder-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
        await api("/api/folders", { method: "POST", json: { name: form.get("name"), parentId: null } });
        event.currentTarget.reset();
        await refreshDashboard();
        toast("Folder created");
    } catch (error) { toast(error.message); }
});

$("#device-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const message = $("#device-message");
    message.textContent = "Registering device…";
    try {
        const result = await api("/api/devices", {
            method: "POST",
            json: { name: form.get("name"), selectedFolderId: form.get("selectedFolderId") }
        });
        const properties = propertiesText(result, form);
        downloadText("sync-client.properties", properties);
        message.textContent = "Downloaded. Keep this file private; its token is shown only once.";
        await refreshDashboard();
    } catch (error) { message.textContent = error.message; }
});

function propertiesText(result, form) {
    const localRoot = String(form.get("localRoot")).trim().replaceAll("\\", "/");
    return [
        `server.base-url=${location.origin}`,
        `sync.local-root=${localRoot}`,
        `sync.remote-folder-id=${result.device.selectedFolderId}`,
        `device.id=${result.device.id}`,
        `device.token=${result.token}`,
        `sync.poll-seconds=${form.get("pollSeconds")}`,
        `sync.full-scan-seconds=${form.get("scanSeconds")}`,
        `sync.max-change-batch=${form.get("maxChangeBatch")}`,
        `sync.ignore=${form.get("ignore") || ""}`,
        ""
    ].join("\n");
}

function downloadText(filename, content) {
    const url = URL.createObjectURL(new Blob([content], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
}

async function updateDeviceFolder(deviceId, folderId) {
    if (!folderId) return;
    try {
        await api(`/api/devices/${deviceId}/folder`, {
            method: "PATCH", json: { selectedFolderId: folderId }
        });
        await refreshDashboard();
        toast("Device folder updated. Update its local properties file too.");
    } catch (error) { toast(error.message); }
}

async function revokeDevice(device) {
    if (!confirm(`Revoke ${device.name}? Its token will stop working.`)) return;
    try {
        await api(`/api/devices/${device.id}`, { method: "DELETE" });
        await refreshDashboard();
        toast("Device revoked");
    } catch (error) { toast(error.message); }
}

document.querySelectorAll("[data-endpoint]").forEach(button => {
    button.addEventListener("click", async () => {
        const output = $("#endpoint-result");
        output.textContent = "Loading…";
        try {
            output.textContent = JSON.stringify(await api(button.dataset.endpoint), null, 2);
        } catch (error) { output.textContent = error.message; }
    });
});

function formatStatus(device) {
    return `${prettyStatus(device.syncStatus)} · cursor ${device.lastProcessedSequence}/${device.latestSequence}`;
}

function prettyStatus(status) { return status.toLowerCase().replaceAll("_", " "); }
function statusClass(status) { return status.toLowerCase().replaceAll("_", "-"); }
function element(tag, className) { const node = document.createElement(tag); if (className) node.className = className; return node; }
function textElement(tag, text, className) { const node = element(tag, className); node.textContent = text; return node; }
function emptyMessage(text) { return textElement("div", text, "empty"); }
function toast(message) {
    const node = $("#toast");
    node.textContent = message;
    node.classList.add("visible");
    window.setTimeout(() => node.classList.remove("visible"), 3200);
}

initialize();
window.setInterval(() => { if (state.user) refreshDashboard(); }, 10000);
