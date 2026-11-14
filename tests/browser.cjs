// Prefer project dependencies; allow the managed runtime without changing tests.
let playwright;
try { playwright = require('playwright'); }
catch (error) {
  if (!process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES) throw error;
  playwright = require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES + '/playwright');
}
module.exports = playwright;
