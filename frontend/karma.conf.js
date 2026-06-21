const path = require('path');

// Configuration Karma. Le rapport LCOV est écrit au chemin déclaré dans
// sonar-project.properties ; le launcher sans sandbox est requis sur les runners CI.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    reporters: ['progress'],
    coverageReporter: {
      dir: path.join(__dirname, 'coverage', 'loyertracker'),
      subdir: '.',
      reporters: [
        { type: 'lcovonly' },
        { type: 'text-summary' },
      ],
    },
    browsers: ['ChromeHeadless'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    restartOnFileChange: true,
  });
};
