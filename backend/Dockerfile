FROM node:20-alpine

WORKDIR /usr/src/app

COPY package*.json ./
RUN npm install --production

COPY . .

ENV NODE_ENV=production
ENV POLL_INTERVAL_MS=30000
ENV MIN_MAGNITUDE=2.0

CMD ["node", "sgc_monitor.js"]
