const { defineConfig } = require('cypress');

module.exports = defineConfig({
  e2e: {
    // Overridable with CYPRESS_BASE_URL so the same suite runs against a local instance or CI's.
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:8080',
    supportFile: 'cypress/support/e2e.js',
    specPattern: 'cypress/e2e/**/*.cy.js',

    // Video recording is off. This is a smoke suite of a server-rendered page: a screenshot of the
    // failure already shows everything a video would, and the recording costs runtime on every run
    // plus artifact storage for footage nobody watches.
    video: false,
    screenshotOnRunFailure: true,

    // Generous enough for a cold JVM page render on a shared CI runner, without being so long that a
    // genuinely hung page takes minutes to report.
    defaultCommandTimeout: 10000,
    pageLoadTimeout: 30000,

    retries: {
      // No retries. A retried pass hides a real race instead of fixing it, and this suite exists to
      // tell the truth about whether the console works.
      runMode: 0,
      openMode: 0,
    },
  },
});
