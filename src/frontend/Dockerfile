FROM node:24-alpine AS build
WORKDIR /workspace
ARG VITE_DINGTALK_CLIENT_ID=
ARG VITE_DINGTALK_CORP_ID=
ENV VITE_DINGTALK_CLIENT_ID=$VITE_DINGTALK_CLIENT_ID
ENV VITE_DINGTALK_CORP_ID=$VITE_DINGTALK_CORP_ID
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
