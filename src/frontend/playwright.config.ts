import { defineConfig, devices } from '@playwright/test'

const iphone15 = devices['iPhone 15']
const iphone11 = devices['iPhone 11']

export default defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:5174',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'mobile-360', use: { ...devices['Desktop Chrome'], viewport: { width: 360, height: 800 } } },
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 768 } } },
    { name: 'iphone11-safari', use: { ...iphone11 } },
    { name: 'iphone15-safari', use: { ...iphone15 } },
    {
      name: 'iphone15-dingtalk',
      use: {
        ...iphone15,
        // DingTalk on iOS embeds the page in WKWebView. Appending its marker keeps
        // WebKit's iPhone device characteristics while exposing the container UA.
        userAgent: `${iphone15.userAgent} DingTalk/7.6.10 iOS`,
      },
    },
  ],
  webServer: {
    command: 'pnpm exec vite --host 127.0.0.1 --port 5174',
    url: 'http://127.0.0.1:5174',
    reuseExistingServer: true,
    env: {
      ...process.env,
      // Public identifiers only. The JSAPI and one-time code exchange are mocked
      // by Playwright, so no enterprise secret or real DingTalk service is used.
      VITE_DINGTALK_CLIENT_ID: 'playwright-ios-client',
      VITE_DINGTALK_CORP_ID: 'playwright-ios-corp',
    },
  },
})
