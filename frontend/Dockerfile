# ── Stage 1: build ──────────────────────────────────────────────
# node 이미지에서 Vue 앱을 빌드해 dist/ 를 만든다
FROM node:24.14.0-alpine AS builder

WORKDIR /app

# 의존성 설치 (package.json + lock 파일만 먼저 복사 → 레이어 캐시 활용)
COPY package.json package-lock.json ./
RUN npm ci

# 소스 복사 후 빌드
COPY . .
RUN npm run build
# 결과물: /app/dist/


# ── Stage 2: serve ───────────────────────────────────────────────
# 빌드 산출물(dist/)만 nginx 이미지로 복사 → 최종 이미지에 node 없음
FROM nginx:1.25-alpine

# dist/ 를 nginx html 루트로 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# nginx 설정 (SPA 라우팅 + /api 프록시)
COPY nginx.conf /etc/nginx/conf.d/default.conf.template

# 컨테이너 시작 시 환경변수를 nginx.conf에 주입 후 nginx 실행
CMD ["/bin/sh", "-c", "envsubst '${BACKEND_HOST}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]

EXPOSE 80