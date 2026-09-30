# Etapa 1: compilar el proyecto React (Node solo se usa aca)
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Etapa 2: servir solo el resultado compilado
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html