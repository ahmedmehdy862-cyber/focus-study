const { originIsSame, clearSessionCookie, json } = require("../../lib/admin-auth");

module.exports = function logout(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  if (!originIsSame(req)) return json(res, 403, { error: "origin_rejected" });
  clearSessionCookie(res);
  return json(res, 200, { ok: true });
};
