FROM node:24-alpine AS build
WORKDIR /build
COPY package.json ./
RUN npm install --ignore-scripts --no-audit --no-fund
COPY scripts scripts
COPY src src
RUN npm run lint && npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
