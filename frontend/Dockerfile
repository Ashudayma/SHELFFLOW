# syntax=docker/dockerfile:1

# ---- Build stage: produce the static SPA bundle with Vite ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Production build. With no .env.production present, VITE_API_BASE_URL defaults to "/api",
# which nginx proxies to the backend service (see nginx.conf). `npm run build` runs `tsc -b && vite build`.
RUN npm run build

# ---- Runtime stage: serve the static bundle via nginx ----
FROM nginx:1.27-alpine AS runtime

# SPA routing + /api -> backend reverse proxy.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
