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

COPY --from=builder /app/package*.json ./
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public

# Install only production dependencies
RUN npm ci --omit=dev

RUN addgroup --system --gid 1001 app && adduser --system --uid 1001 --ingroup app app
RUN chown -R app:app /app
USER app

EXPOSE 3000

CMD ["npm", "run", "start"]
