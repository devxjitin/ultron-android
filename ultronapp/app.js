import { config } from "./config.js";
import { createApi, describeError } from "./api.js";

const DAY = 86_400;
const PAGE_SIZE = 15;
const $ = (id) => document.getElementById(id);
const demo = new URLSearchParams(location.search).has("demo") && ["localhost", "127.0.0.1"].includes(location.hostname);

let api = null;
let licenses = [];
let current = null; // the row open in the detail dialog
let auth = null;
let view = "dashboard";
let page = 0;

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

function showScreen(name) {
  $("signin").hidden = name !== "signin";
  $("app").hidden = name !== "app";
}

function toast(message) {
  const el = $("toast");
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (el.hidden = true), 3400);
}

function showError(id, message) {
  const el = $(id);
  el.textContent = message || "";
  el.hidden = !message;
}

const dateFormat = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });
const formatDate = (seconds) => dateFormat.format(new Date(seconds * 1000));
// The plans on sale. The id is what is stored on the key; days is its validity from first activation.
// price is in rupees and is only the amount the form starts with; what was really paid is stored on each key.
const PLANS = {
  spark: { label: "Spark", days: 7, price: 199, length: "7 days" },
  surge: { label: "Surge", days: 30, price: 699, length: "1 month" },
  apex: { label: "Apex", days: 90, price: 1499, length: "3 months" },
};
const money = (rupees) => `₹${Number(rupees || 0).toLocaleString("en-IN")}`;
/** "Spark" for a known plan; older keys keep their own plan name, capitalised. */
const planLabel = (plan) => PLANS[plan]?.label || (plan ? plan.charAt(0).toUpperCase() + plan.slice(1) : "-");
const STATE_LABEL = { active: "Active", unused: "Not activated", expired: "Expired", revoked: "Revoked" };
const STATE_COLOR = { active: "var(--ok)", unused: "var(--warn)", expired: "var(--idle)", revoked: "var(--bad)" };
const nowSeconds = () => Date.now() / 1000;

function validity(l) {
  if (l.expiresAt) {
    const days = Math.ceil((l.expiresAt - nowSeconds()) / DAY);
    if (days <= 0) return { main: `Expired ${formatDate(l.expiresAt)}`, sub: l.activatedAt ? `Activated ${formatDate(l.activatedAt)}` : "" };
    return { main: `Until ${formatDate(l.expiresAt)}`, sub: `${days} day${days === 1 ? "" : "s"} left` };
  }
  if (l.durationDays) return { main: `${l.durationDays} days`, sub: "starts at first activation" };
  return { main: "Never expires", sub: l.activatedAt ? `Activated ${formatDate(l.activatedAt)}` : "" };
}

// ---------- views

function setView(next) {
  view = next;
  $("view-dashboard").hidden = next !== "dashboard";
  $("view-keys").hidden = next !== "keys";
  $("page-title").textContent = next === "dashboard" ? "Dashboard" : "Keys";
  document.querySelectorAll(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.view === next));
  render();
}

function render() {
  if (view === "dashboard") renderDashboard();
  else renderKeys();
}

// ---------- dashboard

function renderDashboard() {
  const t = nowSeconds();
  const count = (state) => licenses.filter((l) => l.state === state).length;
  const expiring = licenses.filter((l) => l.state === "active" && l.expiresAt && l.expiresAt - t <= 30 * DAY)
    .sort((a, b) => a.expiresAt - b.expiresAt);
  const stat = (label, n, cls = "") => h("div", { class: `stat ${cls}` }, h("b", {}, String(n)), h("span", {}, label));
  // Revoked keys are not counted as revenue (a revoked key is usually a refund) -- except a key that was
  // replaced after the buyer lost it: its payment stands as long as the replacement chain still ends in a live key.
  const replacement = new Map(licenses.filter((l) => l.reissuedFrom).map((l) => [l.reissuedFrom, l]));
  const stillHeld = (l) => {
    for (let i = 0; i < licenses.length && l; i++, l = replacement.get(l.id)) if (l.state !== "revoked") return true;
    return false;
  };
  const paid = licenses.filter(stillHeld);
  const startOfMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 1).getTime() / 1000;
  const sum = (rows) => rows.reduce((total, l) => total + (l.amount || 0), 0);
  $("stats").replaceChildren(
    stat("Revenue", money(sum(paid))),
    stat("Revenue this month", money(sum(paid.filter((l) => l.createdAt >= startOfMonth)))),
    stat("Total keys", licenses.length),
    stat("Active", count("active")),
    stat("Not activated yet", count("unused")),
    stat("Expiring in 30 days", expiring.length, expiring.length ? "warn" : "")
  );

  const total = licenses.length || 1;
  $("state-bar").replaceChildren(...Object.keys(STATE_LABEL).map((s) => {
    const bar = h("i", {});
    bar.style.width = `${(count(s) / total) * 100}%`;
    bar.style.background = STATE_COLOR[s];
    return bar;
  }));
  $("state-legend").replaceChildren(...Object.keys(STATE_LABEL).map((s) => {
    const dot = h("span", { class: "dot" });
    dot.style.background = STATE_COLOR[s];
    return h("li", {}, dot, STATE_LABEL[s], h("b", {}, String(count(s))));
  }));

  const months = [];
  const today = new Date();
  for (let i = 5; i >= 0; i--) months.push(new Date(today.getFullYear(), today.getMonth() - i, 1));
  const counts = months.map((m) => licenses.filter((l) => {
    const d = new Date(l.createdAt * 1000);
    return d.getFullYear() === m.getFullYear() && d.getMonth() === m.getMonth();
  }).length);
  const peak = Math.max(1, ...counts);
  $("month-chart").replaceChildren(...months.map((m, i) => {
    const bar = h("div", { class: "bar" });
    bar.style.height = `${Math.max(2, (counts[i] / peak) * 100)}%`;
    return h("div", { class: "month" }, bar, h("b", {}, String(counts[i])), h("span", {}, m.toLocaleString(undefined, { month: "short" })));
  }));

  const mini = (l, when) => h("button", { class: "mini", type: "button", onclick: () => openDetail(l) },
    h("span", { class: "who" }, h("b", {}, l.name || "No name"), h("span", {}, l.email || l.phone || "No contact details")),
    h("span", { class: "when" }, when));
  const fill = (id, rows, empty) => $(id).replaceChildren(...(rows.length ? rows : [h("div", { class: "mini-empty" }, empty)]));
  fill("expiring", expiring.slice(0, 6).map((l) => mini(l, `${Math.max(0, Math.ceil((l.expiresAt - t) / DAY))}d left`)), "Nothing is about to expire.");
  fill("recent", licenses.slice(0, 6).map((l) => mini(l, formatDate(l.createdAt))), "No keys yet. Create the first one with New key.");
}

// ---------- keys table

function matches(l, query, filter) {
  if (filter !== "all" && l.state !== filter) return false;
  if (!query) return true;
  const haystack = [l.name, l.email, l.phone, l.hint, l.plan, l.note].join(" ").toLowerCase();
  return query.toLowerCase().split(/\s+/).every((word) => haystack.includes(word));
}

function visibleRows() {
  const rows = licenses.filter((l) => matches(l, $("search").value.trim(), $("filter").value));
  const sort = $("sort").value;
  if (sort === "name") rows.sort((a, b) => (a.name || "~").localeCompare(b.name || "~"));
  else if (sort === "expiring") rows.sort((a, b) => (a.expiresAt || Infinity) - (b.expiresAt || Infinity));
  return rows;
}

function renderKeys() {
  const rows = visibleRows();
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  page = Math.min(page, pages - 1);
  const slice = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  $("rows").replaceChildren(...slice.map((l) => {
    const v = validity(l);
    const open = () => openDetail(l);
    return h("tr", { tabindex: "0", onclick: open, onkeydown: (e) => { if (e.key === "Enter") open(); } },
      h("td", {}, h("span", { class: "cell-main" }, l.name || "No name"),
        h("span", { class: "cell-sub" }, [l.email, l.phone].filter(Boolean).join("  |  ") || "No contact details")),
      h("td", {}, h("span", { class: "cell-main" }, planLabel(l.plan)), h("span", { class: "cell-sub" }, `${money(l.amount)} · …${l.hint || "????"}`)),
      h("td", {}, h("span", { class: `badge ${l.state}` }, STATE_LABEL[l.state] || l.state)),
      h("td", {}, h("span", { class: "cell-main" }, v.main), h("span", { class: "cell-sub" }, v.sub)),
      h("td", {}, l.emailedAt ? h("span", { class: "sent" }, "Sent") : h("span", { class: "not-sent" }, "Not sent")));
  }));
  const empty = $("empty");
  empty.hidden = rows.length > 0;
  empty.textContent = licenses.length ? "No keys match this search." : "No keys yet. Create the first one with New key.";
  $("count").textContent = rows.length ? `${page * PAGE_SIZE + 1}–${page * PAGE_SIZE + slice.length} of ${rows.length}` : "";
  $("prev").disabled = page === 0;
  $("next").disabled = page >= pages - 1;
}

/** One CSV cell; a leading = + - @ is defused so a spreadsheet never runs a customer's text as a formula. */
function csvCell(value) {
  let text = String(value ?? "");
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

function exportCsv() {
  const header = ["Name", "Email", "Phone", "Plan", "Amount (INR)", "State", "Created", "Activated", "Expires", "Emailed", "Note"];
  const date = (s) => (s ? new Date(s * 1000).toISOString().slice(0, 10) : "");
  const lines = [header, ...visibleRows().map((l) => [l.name, l.email, l.phone, planLabel(l.plan), l.amount || 0, STATE_LABEL[l.state], date(l.createdAt),
    date(l.activatedAt), date(l.expiresAt), date(l.emailedAt), l.note])];
  const blob = new Blob(["﻿" + lines.map((row) => row.map(csvCell).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" });
  const link = h("a", { href: URL.createObjectURL(blob), download: `ultron-keys-${new Date().toISOString().slice(0, 10)}.csv` });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

async function load() {
  showError("list-error", "");
  $("refresh").disabled = true;
  try {
    licenses = await api.list();
    render();
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
  render();
}

// ---------- form checks

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const PHONE_RE = /^[0-9+()\-\s.]{3,30}$/;

/** Returns {field: message} for everything wrong in [values]; empty when the buyer details are fine. */
function checkBuyer(values) {
  const errors = {};
  if (!values.name) errors.name = "Enter the customer's name.";
  if (!values.email) errors.email = "Enter the customer's email. The key is sent there.";
  else if (!EMAIL_RE.test(values.email)) errors.email = "That email address doesn't look right.";
  if (!values.phone) errors.phone = "Enter the customer's phone number.";
  else if (!PHONE_RE.test(values.phone) || values.phone.replace(/\D/g, "").length < 7) errors.phone = "Use a valid phone number, for example +91 98765 43210.";
  if ("plan" in values && !values.plan) errors.plan = "Choose a plan.";
  return errors;
}

/** An error message when [text] is not a whole number of rupees in range, else "". */
function checkAmount(text) {
  const n = Number(text);
  return text === "" || !Number.isInteger(n) || n < 0 || n > 1_000_000 ? "Enter a whole number of rupees, 0 or more." : "";
}

function showFieldErrors(form, errors) {
  form.querySelectorAll(".field-error").forEach((el) => (el.textContent = errors[el.dataset.for] || ""));
  for (const input of form.elements) if (input.name) input.classList.toggle("invalid", Boolean(errors[input.name]));
  const first = Object.keys(errors)[0];
  if (first) form.elements[first]?.focus();
}

// ---------- create

/** Starts validity and amount from the chosen plan; both stay editable. */
function applyPlan(form, plan) {
  form.elements.durationDays.value = PLANS[plan]?.days ?? 30;
  form.elements.amount.value = PLANS[plan]?.price ?? 0;
}

function openCreate() {
  const form = $("create-form");
  form.reset();
  applyPlan(form, form.elements.plan.value);
  showFieldErrors(form, {});
  showError("create-error", "");
  $("create-dialog").showModal();
  form.elements.name.focus();
}

async function submitCreate(event) {
  event.preventDefault();
  const form = $("create-form");
  const value = (name) => String(form.elements[name].value).trim();
  const body = {
    name: value("name"), email: value("email"), phone: value("phone"), plan: value("plan"),
    durationDays: Number(value("durationDays")), count: Number(value("count") || 1),
    amount: Number(value("amount")), sendEmail: form.elements.sendEmail.checked,
  };
  const errors = checkBuyer(body);
  if (value("durationDays") === "" || !Number.isInteger(body.durationDays) || body.durationDays < 0 || body.durationDays > 3650) {
    errors.durationDays = "Enter a whole number of days, 0 to 3650.";
  }
  if (!Number.isInteger(body.count) || body.count < 1 || body.count > 50) errors.count = "Between 1 and 50.";
  const amountError = checkAmount(value("amount"));
  if (amountError) errors.amount = amountError;
  showFieldErrors(form, errors);
  if (Object.keys(errors).length) return;
  if (value("note")) body.note = value("note");

  $("create-submit").disabled = true;
  showError("create-error", "");
  try {
    const answer = await api.create(body);
    $("create-dialog").close();
    answer.licenses.slice().reverse().forEach(upsert);
    showCreated(answer.keys, answer.emailed, body);
  } catch (e) {
    showError("create-error", describeError(e));
  } finally {
    $("create-submit").disabled = false;
  }
}

function showCreated(keys, emailed, buyer) {
  $("created-title").textContent = keys.length === 1 ? "Key created" : `${keys.length} keys created`;
  const status = $("created-status");
  if (emailed) {
    status.className = "notice ok";
    status.textContent = `Emailed to ${buyer.email}. You can still copy ${keys.length === 1 ? "it" : "them"} below.` +
      (buyer.amount ? ` Recorded ${money(buyer.amount * keys.length)}.` : "");
  } else if (buyer.sendEmail === false) {
    status.className = "notice";
    status.textContent = "Email was skipped. Copy the key and send it to the customer yourself.";
  } else {
    status.className = "notice warn";
    status.textContent = `The email to ${buyer.email} could not be sent. Copy the key and send it yourself, then check the sender in the AWS email service.`;
  }
  $("created-keys").replaceChildren(...keys.map((k) => h("li", { class: "mono" }, k)));
  $("copy-keys").dataset.keys = keys.join("\n");
  $("created-dialog").showModal();
}

async function copyKeys() {
  try {
    await navigator.clipboard.writeText($("copy-keys").dataset.keys);
    toast("Copied");
  } catch {
    toast("Couldn't copy. Select the key and copy it by hand.");
  }
}

// ---------- detail

function toDateInput(seconds) {
  if (!seconds) return "";
  const d = new Date(seconds * 1000);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function endOfDay(value) {
  const [y, m, d] = value.split("-").map(Number);
  return Math.floor(new Date(y, m - 1, d, 23, 59, 59).getTime() / 1000);
}

function timelineItem(done, text, when) {
  return h("li", { class: done ? "done" : "" }, text, when ? h("time", {}, when) : null);
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
  form.elements.amount.value = l.amount || 0;
  $("detail-title").textContent = `Key ending ${l.hint || "(older key)"}`;
  const badge = $("detail-badge");
  badge.className = `badge ${l.state}`;
  badge.textContent = STATE_LABEL[l.state] || l.state;

  const expired = l.expiresAt && l.expiresAt <= nowSeconds();
  $("timeline").replaceChildren(
    timelineItem(Boolean(l.createdAt), "Created", l.createdAt ? formatDate(l.createdAt) : "earlier"),
    timelineItem(Boolean(l.emailedAt), l.emailedAt ? `Emailed to ${l.email}` : "Not emailed", l.emailedAt ? formatDate(l.emailedAt) : ""),
    timelineItem(Boolean(l.activatedAt), l.activatedAt ? (l.onDevice ? "Activated, on a phone now" : "Activated") : "Not activated yet", l.activatedAt ? formatDate(l.activatedAt) : ""),
    timelineItem(Boolean(expired), l.expiresAt ? (expired ? "Expired" : "Valid until") : l.durationDays ? `Valid ${l.durationDays} days from activation` : "Never expires", l.expiresAt ? formatDate(l.expiresAt) : "")
  );

  $("toggle-status").textContent = l.status === "active" ? "Revoke" : "Restore";
  $("toggle-status").classList.toggle("danger", l.status === "active");
  $("allow-move").hidden = !(l.onDevice && l.movedAt > 0);
  showFieldErrors(form, {});
  showError("detail-error", "");
  $("detail-dialog").showModal();
}

function setDetailBusy(busy) {
  for (const id of ["detail-save", "toggle-status", "allow-move", "reissue"]) $(id).disabled = busy;
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

function submitDetail(event) {
  event.preventDefault();
  const form = $("detail-form");
  const fields = form.elements;
  const values = {};
  for (const field of ["name", "email", "phone", "plan", "note"]) values[field] = fields[field].value.trim();
  const errors = checkBuyer(values);
  const amountError = checkAmount(String(fields.amount.value).trim());
  if (amountError) errors.amount = amountError;
  showFieldErrors(form, errors);
  if (Object.keys(errors).length) return;

  const changes = {};
  if (Number(fields.amount.value) !== (current.amount || 0)) changes.amount = Number(fields.amount.value);
  for (const field of ["name", "email", "phone", "plan", "note"]) if (values[field] !== (current[field] || "")) changes[field] = values[field];
  if (!$("days-field").hidden) {
    const days = Number(fields.durationDays.value);
    if (days !== current.durationDays) changes.durationDays = days;
  } else {
    const until = fields.validUntil.value ? endOfDay(fields.validUntil.value) : 0;
    if (toDateInput(until) !== toDateInput(current.expiresAt)) changes.expiresAt = until;
  }
  if (!Object.keys(changes).length) {
    $("detail-dialog").close();
    return;
  }
  saveDetail(() => api.update(current.id, changes), "Saved");
}

function confirmAction(title, text, okLabel) {
  $("confirm-title").textContent = title;
  $("confirm-text").textContent = text;
  $("confirm-yes").textContent = okLabel;
  const dialog = $("confirm-dialog");
  dialog.showModal();
  return new Promise((resolve) => {
    const finish = (answer) => {
      $("confirm-yes").onclick = $("confirm-no").onclick = dialog.onclose = null;
      if (dialog.open) dialog.close();
      resolve(answer);
    };
    $("confirm-yes").onclick = () => finish(true);
    $("confirm-no").onclick = () => finish(false);
    dialog.onclose = () => finish(false);
  });
}

async function reissue() {
  const row = current;
  const ok = await confirmAction(
    "Issue a new key?",
    `The current key ending ${row.hint} stops working at once. A new key with the same plan is created and emailed to ${row.email || "the customer"}.`,
    "Issue and email"
  );
  if (!ok) return;
  setDetailBusy(true);
  showError("detail-error", "");
  try {
    const answer = await api.reissue(row.id);
    $("detail-dialog").close();
    await load();
    showCreated(answer.keys, answer.emailed, { email: row.email });
  } catch (e) {
    showError("detail-error", describeError(e));
  } finally {
    setDetailBusy(false);
  }
}

// ---------- sign in

async function signOut() {
  if (auth) await auth.signOut();
  licenses = [];
  showScreen("signin");
}

async function startWithFirebase() {
  if (!config.firebase.apiKey) {
    showScreen("signin");
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
      showScreen("signin");
      return;
    }
    $("account-email").textContent = user.email || "";
    showScreen("app");
    load();
  });
}

async function startDemo() {
  const { createDemoApi } = await import("./demo.js");
  api = createDemoApi();
  auth = { signOut: async () => {} };
  $("account-email").textContent = "Demo mode, nothing is saved";
  showScreen("app");
  load();
}

function wire() {
  $("sign-out").addEventListener("click", signOut);
  $("refresh").addEventListener("click", load);
  $("new").addEventListener("click", openCreate);
  document.querySelectorAll(".nav-item").forEach((b) => b.addEventListener("click", () => setView(b.dataset.view)));
  for (const id of ["search", "filter", "sort"]) $(id).addEventListener(id === "search" ? "input" : "change", () => { page = 0; renderKeys(); });
  $("prev").addEventListener("click", () => { page -= 1; renderKeys(); });
  $("next").addEventListener("click", () => { page += 1; renderKeys(); });
  $("export").addEventListener("click", exportCsv);
  $("create-form").addEventListener("submit", submitCreate);
  const planSelect = $("create-form").elements.plan;
  planSelect.replaceChildren(...Object.entries(PLANS).map(([id, p]) =>
    h("option", { value: id, selected: id === "surge" }, `${p.label} · ${p.length} · ${money(p.price)}`)));
  $("plans").replaceChildren(...Object.keys(PLANS).map((id) => h("option", { value: id })));
  planSelect.addEventListener("change", (e) => applyPlan($("create-form"), e.target.value));
  $("detail-form").addEventListener("submit", submitDetail);
  $("copy-keys").addEventListener("click", copyKeys);
  $("reissue").addEventListener("click", reissue);
  $("toggle-status").addEventListener("click", () => {
    const revoking = current.status === "active";
    saveDetail(() => api.update(current.id, { status: revoking ? "revoked" : "active" }), revoking ? "Key revoked" : "Key restored");
  });
  $("allow-move").addEventListener("click", () => saveDetail(() => api.allowMove(current.id), "That key can move to a new phone now"));
  document.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", () => b.closest("dialog").close()));
}

wire();
(demo ? startDemo() : startWithFirebase()).catch(() => {
  showScreen("signin");
  showError("signin-error", "Couldn't start. Check your internet and reload.");
});
