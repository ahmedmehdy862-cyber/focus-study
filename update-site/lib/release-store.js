const fs = require("node:fs");
const path = require("node:path");

const RELEASE_PATH = "focus-study/latest.json";
const FALLBACK_PATH = path.join(__dirname, "..", "release.json");

async function readLatestRelease() {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const { get } = await import("@vercel/blob");
      const result = await get(RELEASE_PATH, { access: "public" });
      if (result?.statusCode === 200 && result.stream) {
        const text = await new Response(result.stream).text();
        return JSON.parse(text);
      }
    } catch (error) {
      console.error("Unable to read the latest release record from Blob storage", error?.message || "unknown error");
    }
  }
  return JSON.parse(fs.readFileSync(FALLBACK_PATH, "utf8"));
}

module.exports = { RELEASE_PATH, readLatestRelease };
