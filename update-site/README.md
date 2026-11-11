# Focus Study Updates (Vercel)

Arabic release page with a protected `/admin/` publishing panel and a `/api/latest` update feed consumed by the Android app. The app update button and reminders lead to the store listing; the site stores release metadata, notes, version code, and the store URL.

## Publish a store release

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Publish the signed app to the connected Android store from the store's developer console.
3. Open `/admin/`, sign in, enter the version shown in the store, release notes, APK URL, app description, and QUBIXStore page URL.
4. Publish the notice. The app feed and update page then point users to the store listing; no APK installer link is issued by the app.

## Shared release source and automation

`/api/latest` is the canonical Focus Study record. It feeds the Android update checker and the QUBIXStore `/api/latest` proxy. QUBIXStore's Focus Study card, description, version, and download link are rendered from this live response instead of local hard-coded metadata.

For automatic publication, deploy this project with `FOCUS_RELEASE_SYNC_TOKEN` set to a long random secret and `BLOB_READ_WRITE_TOKEN` configured. Add the same token as GitHub repository secret `FOCUS_RELEASE_SYNC_TOKEN`, along with the Android signing secrets described in `../.github/workflows/focus-study-release.yml`. Push a tag in the form `focus-vX.Y.Z` matching `versionName` in `app/build.gradle.kts`; GitHub Actions builds and signs the APK, publishes a GitHub Release, then posts the version, notes, APK URL, and store URL to `/api/admin/publish-ci`. The version code must increase. The feed and store card refresh within about a minute.

Manual publishing remains available in `/admin/`: upload the signed APK to a public HTTPS release host such as GitHub Releases, paste its `.apk` URL, and publish the same version and notes. Do not publish an APK built with a different signing key or package ID.

For Vercel CLI deployment from this directory:

```powershell
npm install
npm run build
vercel deploy --prod
```

The admin account uses the production environment variables `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `ADMIN_SESSION_SECRET`. Keep these private. The first store URL must be saved through the admin panel before publishing a newer version. Installing from a store preserves app data only when the store listing uses the same package ID and a compatible signing certificate.

## First-install note

The update feed is connected to `https://update-site-sigma.vercel.app/api/latest`. Older installed builds that predate the update checker cannot receive its notifications. Keep the package ID and signing key consistent across releases so store updates can be installed over existing versions.

The current release is `0.12.0` (`versionCode` 13). It adds on-device prayer time calculation from approximate location, configurable calculation methods, and local prayer reminders. The app icon and website mark use the same shield and bolt geometry as the in-app logo.

The app's signing key is private and must remain consistent with the signing configuration used by the connected store.

## Local preview

Run the site through Vercel CLI from this directory with `vercel dev`. Deploying to a Vercel account is a separate publishing step.
