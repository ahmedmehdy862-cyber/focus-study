const crypto = require("node:crypto");

const COOKIE = "__Host-focusstudy_admin";
const SESSION_SECONDS = 12 * 60 * 60;

function safeEqual(left, right) {
  const a = Buffer.from(String(left || ""));
  const b = Buffer.from(String(right || ""));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function parseBody(body) {
  if (body && typeof body === "object") return body;
  try { return JSON.parse(body || "{}"); } catch { return {}; }
}

function originIsSame(req) {
  const origin = req.headers.origin;
  const host = String(req.headers["x-forwarded-host"] || req.headers.host || "").split(",")[0].trim();
  if (!origin || !host) return false;
  try {
    const parsed = new URL(origin);
    return parsed.protocol === "https:" && parsed.host.toLowerCase() === host.toLowerCase();
  } catch {
    return false;
  }
}

function cookieValue(req) {
  const cookies = String(req.headers.cookie || "").split(";");
  const prefix = `${COOKIE}=`;
  const item = cookies.map((value) => value.trim()).find((value) => value.startsWith(prefix));
  return item ? decodeURIComponent(item.slice(prefix.length)) : "";
}

function signature(payload) {
  const key = process.env.ADMIN_SESSION_SECRET;
  if (!key) throw new Error("admin_auth_unconfigured");
  return crypto.createHmac("sha256", key).update(payload).digest("base64url");
}

function createSession(username) {
  const payload = Buffer.from(JSON.stringify({ sub: username, exp: Math.floor(Date.now() / 1000) + SESSION_SECONDS })).toString("base64url");
  return `${payload}.${signature(payload)}`;
}

function isAdmin(req) {
  try {
    const [payload, supplied] = cookieValue(req).split(".");
    if (!payload || !supplied || !safeEqual(supplied, signature(payload))) return false;
    const session = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    return session.sub === process.env.ADMIN_USERNAME && session.exp > Math.floor(Date.now() / 1000);
  } catch {
    return false;
  }
}

function setSessionCookie(res, token) {
  res.setHeader("Set-Cookie", `${COOKIE}=${encodeURIComponent(token)}; Path=/; Max-Age=${SESSION_SECONDS}; HttpOnly; Secure; SameSite=Strict`);
}

function clearSessionCookie(res) {
  res.setHeader("Set-Cookie", `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict`);
}

function json(res, status, data) {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  return res.status(status).json(data);
}

module.exports = { safeEqual, parseBody, originIsSame, createSession, isAdmin, setSessionCookie, clearSessionCookie, json };
