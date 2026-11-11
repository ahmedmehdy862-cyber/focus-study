const { isAdmin, json } = require("../../lib/admin-auth");

module.exports = function session(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "method_not_allowed" });
  if (!process.env.ADMIN_USERNAME || !process.env.ADMIN_SESSION_SECRET) return json(res, 503, { error: "admin_auth_unconfigured" });
  return json(res, 200, { authenticated: isAdmin(req), username: isAdmin(req) ? process.env.ADMIN_USERNAME : null });
};
