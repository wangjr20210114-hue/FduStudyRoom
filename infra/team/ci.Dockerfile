FROM node:24-bookworm-slim AS node
FROM maven:3.9-eclipse-temurin-21
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
RUN apt-get update && apt-get install -y --no-install-recommends git python3 && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/warm/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
WORKDIR /opt/warm/backend
COPY backend ./
RUN sh mvnw -B verify && mkdir -p /opt/warm/m2 && cp -a /root/.m2/. /opt/warm/m2/
RUN chmod -R a+rX /opt/warm && mkdir -p /cache/m2
WORKDIR /workspace
ENTRYPOINT []
CMD ["sleep", "infinity"]
