// An in-memory stand-in for the admin API so the UI can be tried without Firebase or AWS: open index.html?demo=1 on
// localhost. Nothing here talks to the network and nothing is saved.
import { ApiError } from "./api.js";

const DAY = 86_400;
const now = () => Math.floor(Date.now() / 1000);
const ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

function randomId() {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, "0")).join("");
}

function randomKey() {
  const body = Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => ALPHABET[b % 32]).join("");
  return ["ULTR", ...body.match(/.{4}/g)].join("-");
}

function seed() {
  const t = now();
  const base = { movedAt: 0, note: "", status: "active" };
  return [
    { ...base, id: randomId(), hint: "QEMW", plan: "apex", amount: 1499, durationDays: 90, createdAt: t - 40 * DAY, activatedAt: t - 38 * DAY,
      expiresAt: t + 52 * DAY, onDevice: true, name: "Asha Verma", email: "asha@example.com", phone: "+91 98765 43210" },
    { ...base, id: randomId(), hint: "7K2Q", plan: "surge", amount: 699, durationDays: 30, createdAt: t - 3 * DAY, activatedAt: 0,
      expiresAt: 0, onDevice: false, name: "Rohan Mehta", email: "rohan@example.com", phone: "+91 91234 56789", note: "Paid by UPI" },
    { ...base, id: randomId(), hint: "ZC3N", plan: "surge", amount: 699, durationDays: 30, createdAt: t - 70 * DAY, activatedAt: t - 68 * DAY,
      expiresAt: t - 38 * DAY, onDevice: true, name: "Neha Singh", email: "neha@example.com", phone: "" },
    { ...base, id: randomId(), hint: "4H8P", plan: "spark", amount: 199, durationDays: 7, createdAt: t - 12 * DAY, activatedAt: t - 11 * DAY,
      expiresAt: t - 4 * DAY, onDevice: true, name: "Jitin Sengar", email: "jitin@example.com", phone: "+91 90000 00000", note: "Own phone" },
    { ...base, id: randomId(), hint: "M9XD", plan: "apex", amount: 1499, durationDays: 90, createdAt: t - 20 * DAY, activatedAt: 0,
      expiresAt: 0, onDevice: false, status: "revoked", name: "Test buyer", email: "test@example.com", phone: "+91 98000 11111", note: "Refunded" },
    { ...base, id: randomId(), hint: "P5RT", plan: "surge", amount: 699, durationDays: 30, createdAt: t - 33 * DAY, activatedAt: t - 28 * DAY,
      expiresAt: t + 2 * DAY, onDevice: true, name: "Karan Joshi", email: "karan@example.com", phone: "+91 97000 22222" },
  ].map((l) => ({ ...l, emailedAt: l.createdAt + 60 })).map((l) => ({ ...l, state: stateOf(l, t) }));
}

function stateOf(l, t = now()) {
  if (l.status !== "active") return "revoked";
  if (l.expiresAt && l.expiresAt <= t) return "expired";
  return l.onDevice ? "active" : "unused";
}

export function createDemoApi() {
  let rows = seed();
  const pause = () => new Promise((r) => setTimeout(r, 150));
  const find = (id) => {
    const row = rows.find((r) => r.id === id);
    if (!row) throw new ApiError(404, "not_found");
    return row;
  };
  return {
    async list() {
      await pause();
      return rows.map((r) => ({ ...r, state: stateOf(r) })).sort((a, b) => b.createdAt - a.createdAt);
    },
    async create(fields) {
      await pause();
      for (const f of ["name", "email", "phone", "plan"]) if (!String(fields[f] ?? "").trim()) throw new ApiError(400, `${f}_required`);
      if (fields.durationDays == null) throw new ApiError(400, "durationDays_required");
      const keys = [];
      const licenses = [];
      const send = fields.sendEmail !== false;
      for (let i = 0; i < (fields.count || 1); i++) {
        const key = randomKey();
        const row = { id: randomId(), hint: key.slice(-4), status: "active", plan: fields.plan || "surge",
          durationDays: fields.durationDays ?? 30, expiresAt: 0, createdAt: now(), activatedAt: 0, movedAt: 0, onDevice: false,
          name: fields.name || "", email: fields.email || "", phone: fields.phone || "", note: fields.note || "",
          amount: fields.amount ?? 0, emailedAt: send ? now() : 0 };
        row.state = stateOf(row);
        rows.unshift(row);
        keys.push(key);
        licenses.push(row);
      }
      return { keys, licenses, emailed: send };
    },
    async reissue(id) {
      await pause();
      const old = find(id);
      old.status = "revoked";
      old.state = stateOf(old);
      const key = randomKey();
      const row = { ...old, id: randomId(), hint: key.slice(-4), status: "active", expiresAt: 0, createdAt: now(), activatedAt: 0,
        movedAt: 0, onDevice: false, emailedAt: now(), amount: 0 };
      row.state = stateOf(row);
      rows.unshift(row);
      return { keys: [key], licenses: [row], emailed: true };
    },
    async update(id, fields) {
      await pause();
      const row = find(id);
      Object.assign(row, fields);
      row.state = stateOf(row);
      return { ...row };
    },
    async allowMove(id) {
      await pause();
      const row = find(id);
      row.movedAt = 0;
      return { ...row };
    },
  };
}
