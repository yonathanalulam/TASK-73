FROM node:20-alpine AS base
WORKDIR /app
COPY frontend/package*.json ./frontend/
WORKDIR /app/frontend
RUN npm ci
WORKDIR /app
COPY frontend ./frontend
COPY tests/frontend ./frontend/.docker-tests

FROM base AS build
WORKDIR /app/frontend
RUN npm run build

FROM base AS test
WORKDIR /app/frontend
CMD ["npx", "vitest", "run", "--config", "vite.docker-test.config.ts"]

FROM nginx:1.27-alpine AS runtime
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
