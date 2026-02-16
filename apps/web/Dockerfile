# Stage 1: Build Next.js app
FROM node:20-alpine AS builder

WORKDIR /app

ARG NEXT_PUBLIC_API_URL=http://localhost:3001

ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# Layer 1: Dependencies (cached when package.json unchanged)
COPY package*.json ./
RUN npm ci

# Layer 2: Source build
COPY . .
RUN npm run build

# Stage 2: Production server
FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production

RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
