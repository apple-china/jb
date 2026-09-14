FROM node:24-alpine AS build
WORKDIR /workspace
ARG VITE_DINGTALK_CLIENT_ID=
ARG VITE_DINGTALK_CORP_ID=
ARG VITE_ENABLE_MOCK_LOGIN=true
ARG VITE_DINGTALK_AUTO_LOGIN=false
ARG VITE_DINGTALK_TEST_DIAGNOSTICS=false
ENV VITE_DINGTALK_CLIENT_ID=$VITE_DINGTALK_CLIENT_ID
ENV VITE_DINGTALK_CORP_ID=$VITE_DINGTALK_CORP_ID
ENV VITE_ENABLE_MOCK_LOGIN=$VITE_ENABLE_MOCK_LOGIN
ENV VITE_DINGTALK_AUTO_LOGIN=$VITE_DINGTALK_AUTO_LOGIN
ENV VITE_DINGTALK_TEST_DIAGNOSTICS=$VITE_DINGTALK_TEST_DIAGNOSTICS
RUN npm config set registry https://registry.npmmirror.com && npm install -g pnpm@11.19.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
