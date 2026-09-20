/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly DINGTALK_CLIENT_ID: string
  readonly DINGTALK_CORP_ID: string
  readonly DINGTALK_AUTO_LOGIN: string
  readonly MOCK_LOGIN_ENABLED: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
