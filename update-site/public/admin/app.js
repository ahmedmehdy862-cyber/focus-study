import { upload } from "@vercel/blob/client";

const loginPanel = document.getElementById("login-panel");
const adminPanel = document.getElementById("admin-panel");
const loginStatus = document.getElementById("login-status");
const publishStatus = document.getElementById("publish-status");
const storeStatus = document.getElementById("store-status");
const publishButton = document.getElementById("publish");

function showStatus(element, text, type = "") {
  element.textContent = text;
  element.className = `status ${type}`.trim();
}

async function callApi(path, options = {}) {
  const response = await fetch(path, { credentials: "same-origin", cache: "no-store", ...options });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || `http_${response.status}`);
  return body;
}

function errorText(error) {
  const known = {
    invalid_credentials: "اسم المستخدم أو كلمة المرور غير صحيحة.",
    try_later: "محاولات كثيرة. انتظر قليلًا ثم حاول مرة أخرى.",
    admin_auth_unconfigured: "إعداد حساب الإدارة لم يكتمل بعد.",
    storage_not_configured: "تخزين الإصدارات غير مربوط بالموقع بعد.",
    version_code_must_increase: "رقم الإصدار الداخلي يجب أن يكون أكبر من الإصدار المنشور.",
    release_fields_required: "أكمل بيانات الإصدار ورابط المتجر وملاحظات التحديث.",
    invalid_store_url: "أدخل رابط متجر يبدأ بـ HTTPS.",
    invalid_apk_url: "أدخل رابط HTTPS مباشرًا ينتهي بـ .apk.",
    apk_upload_failed: "تعذر رفع ملف APK. تحقق من صلاحية الملف والاتصال.",
    store_link_save_failed: "تعذر حفظ رابط المتجر. تحقق من الرابط وحاول مجددًا.",
    authentication_required: "انتهت الجلسة. سجّل الدخول من جديد."
  };
  return known[error.message] || "تعذر إكمال العملية. تحقق من البيانات والاتصال ثم حاول مجددًا.";
}

async function loadCurrentRelease() {
  const release = await callApi("/api/latest");
  document.getElementById("current-release").textContent = `الإصدار المنشور: ${release.versionName} · رقم ${release.versionCode}`;
  document.getElementById("store-url").value = release.storeUrl || "https://qubixstore.vercel.app/focus-study-updates";
  document.getElementById("apk-url").value = release.apkUrl || "";
  document.getElementById("app-description").value = release.description || "نظّم مهامك وموادك وجلسات تركيزك وخطتك الدراسية في مساحة واحدة.";
  document.getElementById("version-code").value = Number(release.versionCode) + 1;
  const parts = String(release.versionName).split(".").map(Number);
  const nextVersion = (parts[2] || 0) === 0
    ? `${parts[0]}.${(parts[1] || 0) + 1}.0`
    : `${parts[0]}.${parts[1]}.${parts[2] + 1}`;
  document.getElementById("version-name").value = nextVersion;
}

async function openAdmin() {
  const session = await callApi("/api/admin/session");
  loginPanel.classList.toggle("hidden", session.authenticated);
  adminPanel.classList.toggle("hidden", !session.authenticated);
  if (session.authenticated) await loadCurrentRelease();
}

document.getElementById("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.currentTarget.querySelector("button[type=submit]");
  button.disabled = true;
  showStatus(loginStatus, "جارٍ التحقق من بيانات الدخول…");
  try {
    await callApi("/api/admin/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username: document.getElementById("username").value, password: document.getElementById("password").value }) });
    document.getElementById("password").value = "";
    await openAdmin();
  } catch (error) {
    showStatus(loginStatus, errorText(error), "error");
  } finally {
    button.disabled = false;
  }
});

document.getElementById("logout").addEventListener("click", async () => {
  await callApi("/api/admin/logout", { method: "POST" });
  adminPanel.classList.add("hidden");
  loginPanel.classList.remove("hidden");
  showStatus(loginStatus, "تم تسجيل الخروج.", "ok");
});

document.getElementById("store-link-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = document.getElementById("save-store-link");
  const storeUrl = document.getElementById("store-url").value.trim();
  const apkUrl = document.getElementById("apk-url").value.trim();
  button.disabled = true;
  showStatus(storeStatus, "جارٍ حفظ رابط المتجر…");
  try {
    await callApi("/api/admin/store-link", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ storeUrl, apkUrl }) });
    showStatus(storeStatus, "تم ربط صفحة المتجر بالإصدار الحالي. ستفتحها إشعارات التحديث.", "ok");
  } catch (error) {
    showStatus(storeStatus, errorText(error), "error");
  } finally {
    button.disabled = false;
  }
});

document.getElementById("release-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const versionName = document.getElementById("version-name").value.trim();
  const versionCode = Number(document.getElementById("version-code").value);
  const releaseNotes = document.getElementById("release-notes").value.trim();
  const storeUrl = document.getElementById("store-url").value.trim();
  const apkInput = document.getElementById("apk-url");
  let apkUrl = apkInput.value.trim();
  const apkFile = document.getElementById("apk-file").files?.[0];
  const description = document.getElementById("app-description").value.trim();
  if (!Number.isInteger(versionCode) || !/^\d+\.\d+\.\d+$/.test(versionName) || !releaseNotes || !storeUrl || (!apkUrl && !apkFile)) return showStatus(publishStatus, "راجع رقم الإصدار ورابط المتجر وملاحظات التحديث، واختر APK أو أدخل رابطه.", "error");
  if (apkFile && (apkFile.size > 200 * 1024 * 1024 || !apkFile.name.toLowerCase().endsWith(".apk"))) return showStatus(publishStatus, "اختر ملف APK صالحًا لا يتجاوز 200 ميجابايت.", "error");

  publishButton.disabled = true;
  showStatus(publishStatus, apkFile ? "جارٍ رفع ملف APK بأمان…" : "جارٍ نشر بيانات الإصدار ورابط المتجر…");
  try {
    if (apkFile) {
      const safeName = `${versionCode}-${crypto.randomUUID()}.apk`;
      const blob = await upload(`focus-study/releases/${safeName}`, apkFile, {
        access: "public",
        handleUploadUrl: "/api/admin/upload-apk",
        contentType: "application/vnd.android.package-archive"
      });
      apkUrl = blob.url;
      apkInput.value = apkUrl;
    }
    await callApi("/api/admin/publish", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ versionCode, versionName, releaseNotes, storeUrl, apkUrl, description }) });
    showStatus(publishStatus, `تم نشر إعلان Focus Study ${versionName}. سيفتح إشعار التحديث صفحة المتجر.`, "ok");
    await loadCurrentRelease();
    document.getElementById("release-notes").value = "";
  } catch (error) {
    showStatus(publishStatus, errorText(error), "error");
  } finally {
    publishButton.disabled = false;
  }
});

openAdmin().catch((error) => showStatus(loginStatus, errorText(error), "error"));
