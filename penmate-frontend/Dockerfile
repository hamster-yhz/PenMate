# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS build

WORKDIR /workspace/penmate-frontend

ARG NPM_CONFIG_REGISTRY=
COPY penmate-frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    if [ -n "$NPM_CONFIG_REGISTRY" ]; then \
      npm ci --registry="$NPM_CONFIG_REGISTRY"; \
    else \
      npm ci; \
    fi

COPY penmate-frontend ./
ARG VITE_APP_API_BASE_URL=/api
ARG VITE_STORAGE_URL_PROTOCOL=http
ENV VITE_APP_API_BASE_URL=${VITE_APP_API_BASE_URL} \
    VITE_STORAGE_URL_PROTOCOL=${VITE_STORAGE_URL_PROTOCOL}
RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY penmate-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/penmate-frontend/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
  CMD wget --no-check-certificate -qO- https://127.0.0.1:8443/ >/dev/null || exit 1
