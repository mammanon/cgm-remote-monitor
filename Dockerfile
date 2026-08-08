FROM node:22-slim

# git is needed because a few dependencies are installed straight from GitHub
RUN apt-get update \
 && apt-get install -y --no-install-recommends git ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/app

COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY . .

ENV PORT=1337
EXPOSE 1337

CMD ["node", "server.js"]
