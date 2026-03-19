# Node.js 빌드 (React + TypeScript + Vite)
FROM node:22-alpine AS builder
WORKDIR /app

# 의존성 캐시를 위해 package.json 먼저 복사
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# 환자용 모바일 앱 — API base URL 주입
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# Nginx로 정적 파일 서빙 (포트 3001)
FROM nginx:alpine
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3001
CMD ["nginx", "-g", "daemon off;"]
