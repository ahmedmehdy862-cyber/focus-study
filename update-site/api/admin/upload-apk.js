const { handleUpload } = require("@vercel/blob/client");
const { isAdmin, originIsSame, parseBody, json } = require("../../lib/admin-auth");

module.exports = async function uploadApk(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  if (!process.env.BLOB_READ_WRITE_TOKEN) return json(res, 503, { error: "storage_not_configured" });
  try {
    const result = await handleUpload({
      request: req,
      body: parseBody(req.body),
      onBeforeGenerateToken: async (pathname) => {
        if (!originIsSame(req) || !isAdmin(req)) throw new Error("authentication_required");
        if (!/^focus-study\/releases\/[0-9]+-[a-f0-9-]+\.apk$/i.test(pathname)) throw new Error("invalid_upload_path");
        return {
          allowedContentTypes: ["application/vnd.android.package-archive", "application/octet-stream"],
          maximumSizeInBytes: 200 * 1024 * 1024,
          addRandomSuffix: false,
          allowOverwrite: false,
          cacheControlMaxAge: 31536000
        };
      }
    });
    return json(res, 200, result);
  } catch (error) {
    const reason = error?.message === "authentication_required" ? "authentication_required" : "apk_upload_failed";
    return json(res, reason === "authentication_required" ? 401 : 400, { error: reason });
  }
};
