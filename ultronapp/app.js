import { config } from "./config.js";
import { createApi, describeError } from "./api.js";

const DAY = 86_400;
const $ = (id) => document.getElementById(id);
const demo = new URLSearchParams(location.search).has("demo") && ["localhost", "127.0.0.1"].includes(location.hostname);

let api = null;
let licenses = [];
let current = null; // the row open in the detail dialog
let auth = null;

/** Tiny element builder; text goes in as text, never as HTML. */
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [name, value] of Object.entries(props)) {
    if (name === "class") el.className = value;
    else if (name.startsWith("on")) el.addEventListener(name.slice(2), value);
    else if (value !== false && value != null) el.setAttribute(name, value === true ? "" : value);
  }
  for (const child of children.flat()) if (child != null) el.append(child);
  return el;
}

function show(view) {
  $("signin").hidden = view !== "signin";
  $("dashboard").hidden = view !== "dashboard";
}

function toast(message) {
  const el = $("toast");
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.hidden = true), 3200);
}

function showError(id, message) {
  const el = $(id);
  el.textContent = message || "";
  el.hidden = !message;
}

const dateFormat = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });
const formatDate = (seconds) => dateFormat.format(new Date(seconds * 1000));
const STATE_LABEL = { active: "Active", unused: "Not activated", expired: "Expired", revoked: "Revoked" };

function validity(l) {
  const now = Date.now() / 1000;
  if (l.expiresAt) {
    const days = Math.ceil((l.expiresAt - now) / DAY);
    if (days <= 0) return { main: `Expired ${formatDate(l.expiresAt)}`, sub: l.activatedAt ? `Activated ${formatDate(l.activatedAt)}` : "" };
    return { main: `Until ${formatDate(l.expiresAt)}`, sub: `${days} day${days === 1 ? "" : "s"} left` };
  }
  if (l.durationDays) return { main: `${l.durationDays} days`, sub: "starts at first activation" };
  return { main: "Never expires", sub: l.activatedAt ? `Activated ${formatDate(l.activatedAt)}` : "" };
}

function matches(l, query, filter) {
  if (filter !== "all" && l.state !== filter) return false;
  if (!query) return true;
  const haystack = [l.name, l.email, l.phone, l.hint, l.plan, l.note].join(" ").toLowerCase();
  return query.toLowerCase().split(/\s+/).every((word) => haystack.includes(word));
}

function renderStats() {
  const count = (state) => licenses.filter((l) => l.state === state).length;
  $("stats").replaceChildren(
    ...[["Total keys", licenses.length], ["Active", count("active")], ["Not activated", count("unused")],
      ["Expired or revoked", count("expired") + count("revoked")]]
      .map(([label, n]) => h("div", { class: "stat" }, h("b", {}, String(n)), h("span", {}, label)))
  );
}

function renderList() {
  renderStats();
  const rows = licenses.filter((l) => matches(l, $("search").value.trim(), $("filter").value));
  const list = $("list");
  if (!rows.length) {
    list.replaceChildren(h("div", { class: "empty" }, licenses.length ? "No keys match this search." : "No keys yet. Create the first one with New key."));
    return;
  }
  list.replaceChildren(
    h("div", { class: "row head", "aria-hidden": "true" }, h("span", {}, "Customer"), h("span", {}, "Plan"), h("span", {}, "State"), h("span", {}, "Validity")),
    ...rows.map((l) => {
      const v = validity(l);
      return h("button", { class: "row", type: "button", onclick: () => openDetail(l) },
        h("span", { class: "cell c-customer" },
          h("span", { class: "main" }, l.name || "No name"),
          h("span", { class: "sub" }, [l.email, l.phone].filter(Boolean).join("  |  ") || "No contact details")),
        h("span", { class: "cell c-plan" },
          h("span", { class: "main" }, l.plan || "-"),
          h("span", { class: "sub mono" }, `ULTR-••••-••••-••••-${l.hint || "••••"}`)),
        h("span", { class: "cell c-state" }, h("span", { class: `badge ${l.state}` }, STATE_LABEL[l.state] || l.state)),
        h("span", { class: "cell c-valid" }, h("span", { class: "main" }, v.main), h("span", { class: "sub" }, v.sub)));
    })
  );
}

async function load() {
  showError("list-error", "");
  $("refresh").disabled = true;
  try {
    licenses = await api.list();
    renderList();
  } catch (e) {
    showError("list-error", describeError(e));
    if (e.status === 403) {
      await signOut();
      showError("signin-error", describeError(e));
    }
  } finally {
    $("refresh").disabled = false;
  }
}

function upsert(row) {
  const i = licenses.findIndex((l) => l.id === row.id);
  if (i >= 0) licenses[i] = row;
  else licenses.unshift(row);
  renderList();
}

// ---- create

function openCreate() {
  $("create-form").reset();
  showError("create-error", "");
  $("create-dialog").showModal();
}

async function submitCreate(event) {
  event.preventDefault();
  const form = new FormData($("create-form"));
  const body = {
    plan: form.get("plan").trim(),
    durationDays: Number(form.get("durationDays")),
    count: Number(form.get("count") || 1),
  };
  for (const field of ["name", "email", "phone", "note"]) {
    const value = form.get(field).trim();
    if (value) body[field] = value;
  }
  $("create-submit").disabled = true;
  showError("create-error", "");
  try {
    const { keys, licenses: created } = await api.create(body);
    $("create-dialog").close();
    created.slice().reverse().forEach(upsert);
    showCreated(keys);
  } catch (e) {
    showError("create-error", describeError(e));
  } finally {
    $("create-submit").disabled = false;
  }
}

function showCreated(keys) {
  $("created-title").textContent = keys.length === 1 ? "Key created" : `${keys.length} keys created`;
  $("created-keys").replaceChildren(...keys.map((k) => h("li", { class: "mono" }, k)));
  $("copy-keys").dataset.keys = keys.join("\n");
  $("created-dialog").showModal();
}

async function copyKeys() {
  const text = $("copy-keys").dataset.keys;
  try {
    await navigator.clipboard.writeText(text);
    toast("Copied");
  } catch {
    toast("Couldn't copy. Select the key and copy it by hand.");
  }
}

// ---- detail

function toDateInput(seconds) {
  if (!seconds) return "";
  const d = new Date(seconds * 1000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function endOfDay(value) {
  const [y, m, d] = value.split("-").map(Number);
  return Math.floor(new Date(y, m - 1, d, 23, 59, 59).getTime() / 1000);
}

function openDetail(l) {
  current = l;
  const form = $("detail-form");
  form.reset();
  for (const field of ["name", "email", "phone", "plan", "note"]) form.elements[field].value = l[field] || "";
  const started = l.activatedAt > 0 || l.expiresAt > 0;
  $("days-field").hidden = started;
  $("until-field").hidden = !started;
  form.elements.durationDays.value = l.durationDays;
  form.elements.validUntil.value = toDateInput(l.expiresAt);
  $("detail-title").textContent = `Key ending ${l.hint || "(older key)"}`;
  const facts = [`Created ${l.createdAt ? formatDate(l.createdAt) : "earlier"}`];
  facts.push(l.activatedAt ? `activated ${formatDate(l.activatedAt)}` : "not activated yet");
  facts.push(l.onDevice ? "on a phone now" : "not on any phone");
  $("detail-facts").textContent = facts.join(", ") + ".";
  $("toggle-status").textContent = l.status === "active" ? "Revoke" : "Restore";
  $("toggle-status").classList.toggle("danger", l.status === "active");
  $("allow-move").hidden = !(l.onDevice && l.movedAt > 0);
  showError("detail-error", "");
  $("detail-dialog").showModal();
}

async function saveDetail(changes, doneMessage) {
  showError("detail-error", "");
  setDetailBusy(true);
  try {
    const row = await changes();
    upsert(row);
    current = row;
    $("detail-dialog").close();
    toast(doneMessage);
  } catch (e) {
    showError("detail-error", describeError(e));
  } finally {
    setDetailBusy(false);
  }
}

function setDetailBusy(busy) {
  for (const id of ["detail-save", "toggle-status", "allow-move"]) $(id).disabled = busy;
}

function submitDetail(event) {
  event.preventDefault();
  const form = $("detail-form").elements;
  const changes = {};
  for (const field of ["name", "email", "phone", "plan", "note"]) {
    const value = form[field].value.trim();
    if (value !== (current[field] || "")) changes[field] = value;
  }
  if (!$("days-field").hidden) {
    const days = Number(form.durationDays.value);
    if (days !== current.durationDays) changes.durationDays = days;
  } else {
    const until = form.validUntil.value ? endOfDay(form.validUntil.value) : 0;
    if (toDateInput(until) !== toDateInput(current.expiresAt)) changes.expiresAt = until;
  }
  if (!Object.keys(changes).length) {
    $("detail-dialog").close();
    return;
  }
  saveDetail(() => api.update(current.id, changes), "Saved");
}

// ---- sign in

async function signOut() {
  if (auth) await auth.signOut();
  licenses = [];
  $("account").hidden = true;
  show("signin");
}

async function startWithFirebase() {
  if (!config.firebase.apiKey) {
    show("signin");
    $("google").disabled = true;
    showError("signin-error", "Add the Firebase web apiKey to ultronapp/config.js first (see the comment there).");
    return;
  }
  const version = "10.12.2";
  const [{ initializeApp }, firebaseAuth] = await Promise.all([
    import(`https://www.gstatic.com/firebasejs/${version}/firebase-app.js`),
    import(`https://www.gstatic.com/firebasejs/${version}/firebase-auth.js`),
  ]);
  const app = initializeApp(config.firebase);
  const fbAuth = firebaseAuth.getAuth(app);
  auth = { signOut: () => firebaseAuth.signOut(fbAuth) };
  api = createApi(config.apiBase, () => fbAuth.currentUser.getIdToken());

  $("google").addEventListener("click", async () => {
    showError("signin-error", "");
    try {
      await firebaseAuth.signInWithPopup(fbAuth, new firebaseAuth.GoogleAuthProvider());
    } catch (e) {
      if (e.code !== "auth/popup-closed-by-user" && e.code !== "auth/cancelled-popup-request") {
        showError("signin-error", "Google sign-in failed. Check that this domain is in Firebase Authorized domains.");
      }
    }
  });

  firebaseAuth.onAuthStateChanged(fbAuth, (user) => {
    if (!user) {
      $("account").hidden = true;
      show("signin");
      return;
    }
    $("account-email").textContent = user.email || "";
    $("account").hidden = false;
    show("dashboard");
    load();
  });
}

async function startDemo() {
  const { createDemoApi } = await import("./demo.js");
  api = createDemoApi();
  auth = { signOut: async () => {} };
  $("account-email").textContent = "demo mode, nothing is saved";
  $("account").hidden = false;
  show("dashboard");
  load();
}

function wire() {
  $("sign-out").addEventListener("click", signOut);
  $("refresh").addEventListener("click", load);
  $("new").addEventListener("click", openCreate);
  $("search").addEventListener("input", renderList);
  $("filter").addEventListener("change", renderList);
  $("create-form").addEventListener("submit", submitCreate);
  $("detail-form").addEventListener("submit", submitDetail);
  $("copy-keys").addEventListener("click", copyKeys);
  $("toggle-status").addEventListener("click", () => {
    const revoking = current.status === "active";
    saveDetail(() => api.update(current.id, { status: revoking ? "revoked" : "active" }), revoking ? "Key revoked" : "Key restored");
  });
  $("allow-move").addEventListener("click", () => saveDetail(() => api.allowMove(current.id), "That key can move to a new phone now"));
  document.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", () => b.closest("dialog").close()));
}

wire();
(demo ? startDemo() : startWithFirebase()).catch(() => {
  show("signin");
  showError("signin-error", "Couldn't start. Check your internet and reload.");
});
