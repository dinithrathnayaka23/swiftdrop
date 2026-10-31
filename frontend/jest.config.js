/**
 * Jest configuration. TypeScript/TSX is compiled by SWC (fast, no type checking: `tsc -b` does
 * that). Tests run in a simulated browser (jsdom).
 * @type {import('jest').Config}
 */
export default {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  setupFiles: ['<rootDir>/jest.polyfills.js'],
  setupFilesAfterEnv: ['<rootDir>/src/test/setup.ts'],
  transform: {
    '^.+\\.(tsx?|m?jsx?)$': [
      '@swc/jest',
      {
        jsc: {
          parser: { syntax: 'typescript', tsx: true },
          transform: { react: { runtime: 'automatic' } },
        },
      },
    ],
  },
  // react-router (and its helpers) ship only as ES modules, so they are compiled too.
  // ([/\\] matches both / and the \ in Windows paths.)
  transformIgnorePatterns: [
    '[/\\\\]node_modules[/\\\\](?!(react-router|@remix-run|cookie-es)[/\\\\])',
  ],
  moduleNameMapper: {
    // Vite's import.meta.env doesn't exist under Jest: tests get fixed values instead.
    '^(.*/)?utils/env$': '<rootDir>/src/test/env.ts',
    // axios resolves to its ESM build for browsers; use the CommonJS one.
    '^axios$': '<rootDir>/node_modules/axios/dist/browser/axios.cjs',
    '\\.css$': '<rootDir>/src/test/styleStub.ts',
    // React Router's framework-mode route loader uses Vite's import.meta.hot, which can't be
    // compiled for Jest. This app uses plain <Routes>, so the loader is never called.
    '^\\./routeModules\\.js$': '<rootDir>/src/test/reactRouterRouteModulesStub.ts',
  },
  clearMocks: true,
  collectCoverageFrom: ['src/**/*.{ts,tsx}', '!src/test/**', '!src/main.tsx', '!src/**/*.d.ts'],
  coverageReporters: ['text', 'html', 'lcov'],
};
