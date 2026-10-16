# Multi-stage build: Node builds the static bundle, nginx serves it.
FROM node:20-alpine AS build
WORKDIR /app
# Install from the lockfile first so this layer is cached unless it changes.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
