const esbuild = require("esbuild");

esbuild.buildSync({
  entryPoints: ["public/admin/app.js"],
  bundle: true,
  platform: "browser",
  format: "esm",
  target: ["es2020"],
  outfile: "public/admin/app.bundle.js",
  minify: true
});
