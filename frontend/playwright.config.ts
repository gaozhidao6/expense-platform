import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure',
    // Traces can contain Authorization headers: keep them local and out of CI artifacts.
    trace: 'off',
    video: process.env.DISABLE_VIDEO === 'true' ? 'off' : process.env.RECORD_DEMO === 'true' ? 'on' : 'retain-on-failure',
    channel: process.env.PLAYWRIGHT_CHANNEL,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } }],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
