const { safeEqual, parseBody, json } = require("../../lib/admin-auth");
const { put } = require("@vercel/blob");
const { RELEASE_PATH, readLatestRelease } = require("../../lib/release-store");

module.exports = async function publishFromCi(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  const expected = process.env.FOCUS_RELEASE_SYNC_TOKEN;
  const supplied = String(req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!expected || !safeEqual(supplied, expected)) return json(res, 401, { error: "authentication_required" });
  if (!process.env.BLOB_READ_WRITE_TOKEN) return json(res, 503, { error: "storage_not_configured" });

  try {
    const body = parseBody(req.body);
    const versionCode = Number(body.versionCode);
    const versionName = String(body.versionName || "").trim();
    const releaseNotes = String(body.releaseNotes || "").trim().slice(0, 1600);
    const apkUrl = String(body.apkUrl || "").trim();
    const storeUrl = String(body.storeUrl || process.env.FOCUS_STORE_URL || "https://qubixstore.vercel.app/focus-study-updates").trim();
    const description = String(body.description || "نظّم مهامك وموادك وجلسات تركيزك وخطتك الدراسية في مساحة واحدة.").trim().slice(0, 500);
    if (!Number.isInteger(versionCode) || versionCode < 1 || !/^\d+\.\d+\.\d+$/.test(versionName) || !releaseNotes || !apkUrl) {
      return json(res, 400, { error: "release_fields_required" });
    }
    const current = await readLatestRelease();
    if (versionCode <= Number(current.versionCode || 0)) return json(res, 409, { error: "version_code_must_increase", currentVersionCode: current.versionCode });

    const validHttps = (value) => {
      try {
        const url = new URL(value);
        return url.protocol === "https:" && Boolean(url.hostname) && !url.username && !url.password;
      } catch { return false; }
    };
    let apk;
    try { apk = new URL(apkUrl); } catch { return json(res, 400, { error: "invalid_apk_url" }); }
    if (!validHttps(apkUrl) || !apk.pathname.toLowerCase().endsWith(".apk")) return json(res, 400, { error: "invalid_apk_url" });
    if (!validHttps(storeUrl)) return json(res, 400, { error: "invalid_store_url" });
    const host = String(req.headers["x-forwarded-host"] || req.headers.host || "").split(",")[0].trim();
    if (!host) return json(res, 400, { error: "release_host_missing" });

    const release = {
      packageName: "com.focusstudy.poc", versionCode, versionName, storeUrl, apkUrl, description,
      releasePageUrl: `https://${host}/#download`, releaseNotes, publishedAt: new Date().toISOString()
    };
    await put(RELEASE_PATH, JSON.stringify(release), {
      access: "public", addRandomSuffix: false, allowOverwrite: true,
      contentType: "application/json; charset=utf-8", cacheControlMaxAge: 60
    });
    return json(res, 200, { ok: true, packageName: release.packageName, versionCode, versionName, storeUrl, apkUrl });
  } catch (error) {
    console.error("CI release sync failed", error?.message || "unknown error");
    return json(res, 500, { error: "release_publish_failed" });
  }
};
