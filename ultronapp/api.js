// Calls to the admin routes of the Ultron API (mcp/license-admin). Every call carries the signed-in admin's Firebase token.

export class ApiError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

export function createApi(baseUrl, getToken) {
  async function call(method, path, body) {
    const token = await getToken();
    let response;
    try {
      response = await fetch(baseUrl.replace(/\/$/, "") + path, {
        method,
        headers: { authorization: `Bearer ${token}`, ...(body ? { "content-type": "application/json" } : {}) },
        body: body ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new ApiError(0, "network");
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new ApiError(response.status, data.error || data.message || `http_${response.status}`);
    return data;
  }

  return {
    list: () => call("GET", "/v1/admin/licenses").then((d) => d.licenses),
    create: (fields) => call("POST", "/v1/admin/licenses", fields),
    update: (id, fields) => call("PATCH", `/v1/admin/licenses/${id}`, fields).then((d) => d.license),
    allowMove: (id) => call("POST", `/v1/admin/licenses/${id}/allow-move`).then((d) => d.license),
    // A key can't be read back, so a lost one is replaced: the old key is revoked and a new one is created and emailed.
    reissue: (id) => call("POST", `/v1/admin/licenses/${id}/reissue`),
  };
}

const MESSAGES = {
  network: "Couldn't reach the server. Check your internet and try again.",
  forbidden: "This Google account is not allowed to manage Ultron keys.",
  bad_email: "That email address doesn't look right.",
  bad_phone: "Phone can only have digits, spaces, + ( ) - and be 3 to 30 characters.",
  name_too_long: "Name is too long (80 characters at most).",
  email_too_long: "Email is too long.",
  phone_too_long: "Phone is too long.",
  note_too_long: "Note is too long (300 characters at most).",
  name_required: "Enter the customer's name.",
  email_required: "Enter the customer's email. The key is sent there.",
  phone_required: "Enter the customer's phone number.",
  plan_required: "Choose a plan.",
  durationDays_required: "Enter how many days the key is valid (0 for never expires).",
  details_missing: "This key has no name, email or phone, so a new one can't be emailed. Fill them in first.",
  plan_too_long: "Plan name is too long.",
  bad_durationDays: "Validity must be a whole number of days, 0 to 3650.",
  bad_count: "How many keys: 1 to 50.",
  bad_amount: "Amount must be a whole number of rupees, 0 to 10,00,000.",
  not_found: "That key no longer exists. Refresh the list.",
};

export function describeError(error) {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Your sign-in expired. Sign in again.";
    return MESSAGES[error.code] || "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}
