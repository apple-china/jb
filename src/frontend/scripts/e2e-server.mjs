import { build, preview } from 'vite'

await build({ configFile: 'vite.config.ts', mode: 'dev' })
const server = await preview({
  configFile: 'vite.config.ts',
  mode: 'dev',
  preview: { host: '127.0.0.1', port: 5174, strictPort: true },
})
