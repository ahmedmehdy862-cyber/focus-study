const { safeEqual, parseBody, originIsSame, createSession, setSessionCookie, json } = require("../../lib/admin-auth");

const attempts = new Map();

module.exports = async function login(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  if (!originIsSame(req)) return json(res, 403, { error: "origin_rejected" });
  const username = process.env.ADMIN_USERNAME;
  const password = process.env.ADMIN_PASSWORD;
  if (!username || !password || !process.env.ADMIN_SESSION_SECRET) return json(res, 503, { error: "admin_auth_unconfigured" });

  const address = String(req.headers["x-forwarded-for"] || "unknown").split(",")[0].trim();
  const now = Date.now();
  const current = attempts.get(address);
  if (current && current.until > now && current.count >= 8) return json(res, 429, { error: "try_later" });
  if (!current || current.until <= now) attempts.set(address, { count: 0, until: now + 15 * 60 * 1000 });

  const body = parseBody(req.body);
  if (!safeEqual(body.username, username) || !safeEqual(body.password, password)) {
    const state = attempts.get(address);
    attempts.set(address, { count: (state?.count || 0) + 1, until: state?.until || now + 15 * 60 * 1000 });
    return json(res, 401, { error: "invalid_credentials" });
  }

  attempts.delete(address);
  setSessionCookie(res, createSession(username));
  return json(res, 200, { ok: true, username });
};
