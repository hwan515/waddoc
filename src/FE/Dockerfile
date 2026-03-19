# Node.js 빌드 (React + Vite)
FROM node:22-alpine AS builder
WORKDIR /app

# 의존성 캐시를 위해 package.json 먼저 복사
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# Vite 빌드 시 API 프록시 경로 주입 (nginx에서 /api → spring-api로 프록시)
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# Nginx로 정적 파일 서빙
FROM nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
