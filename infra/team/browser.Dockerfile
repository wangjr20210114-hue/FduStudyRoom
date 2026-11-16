ARG BASE_IMAGE=fdu-campus-ci:core
FROM ${BASE_IMAGE}
ARG CHROME_VERSION=157.0.8103.78
ARG CHROME_SHA256=f90329720fb61e5a76da9b6b509b6ad12a484c9c5df0359403e935177c6d153a
RUN apt-get update && apt-get install -y --no-install-recommends libnss3 libatk1.0-0t64 libatk-bridge2.0-0t64 libcups2t64 libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 libasound2t64 libpango-1.0-0 libcairo2 fonts-liberation fonts-noto-cjk && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL --retry 2 "https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chrome-headless-shell-linux64.zip" -o /tmp/campus-browser.zip && echo "${CHROME_SHA256}  /tmp/campus-browser.zip" | sha256sum -c - && mkdir -p /opt/campus-browser && unzip -q /tmp/campus-browser.zip -d /opt/campus-browser && rm /tmp/campus-browser.zip && chmod -R a+rX /opt/campus-browser
ENV CAMPUS_TEST_BROWSER=/opt/campus-browser/chrome-headless-shell-linux64/chrome-headless-shell
