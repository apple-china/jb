FROM node:24-alpine AS build
WORKDIR /workspace
ARG DINGTALK_CLIENT_ID=
ARG DINGTALK_CORP_ID=
ARG DINGTALK_AUTO_LOGIN=
ARG MOCK_LOGIN_ENABLED=
ARG VITE_MODE=dev
ENV DINGTALK_CLIENT_ID=$DINGTALK_CLIENT_ID
ENV DINGTALK_CORP_ID=$DINGTALK_CORP_ID
ENV DINGTALK_AUTO_LOGIN=$DINGTALK_AUTO_LOGIN
ENV MOCK_LOGIN_ENABLED=$MOCK_LOGIN_ENABLED
RUN npm config set registry https://registry.npmmirror.com && npm install -g pnpm@11.19.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build -- --mode "$VITE_MODE"

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
