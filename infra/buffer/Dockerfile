FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends python3 git ca-certificates && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 buffer && useradd -r -u 10001 -g buffer buffer
WORKDIR /workspace
COPY --chown=10001:10001 app.jar /opt/buffer/app.jar
COPY --chown=10001:10001 web/ tools/buffer/frontend/dist/
COPY --chown=10001:10001 spec/ spec/
COPY --chown=10001:10001 tools/ tools/
RUN ln -s /data/.campus /workspace/.campus
USER 10001:10001
ENTRYPOINT ["java","-jar","/opt/buffer/app.jar"]
