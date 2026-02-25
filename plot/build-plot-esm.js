const esbuild = require("esbuild");

// Path to the source entry
const entryFile = "node_modules/@observablehq/plot/src/index.js";

esbuild.build({
    entryPoints: [entryFile],
    bundle: true,
    format: "esm",
    outfile: "build/plot.esm.js",
    minify: true,
    sourcemap: true
}).catch(() => process.exit(1));
