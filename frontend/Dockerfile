# Multi-stage Dockerfile for c2csectrade Frontend

# Stage 1: Build stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm ci

# Copy source code and build
COPY . .
RUN npm run build

# Stage 2: Production stage with Nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Install curl for health check
RUN apk add --no-cache curl

# Remove default nginx page
RUN rm -rf ./*

# Copy build artifacts from build stage
COPY --from=build /app/dist .

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Health check (using curl)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:80 || exit 1

# Start nginx
CMD ["nginx", "-g", "daemon off;"]

