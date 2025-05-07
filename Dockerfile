FROM eclipse-temurin:21 as jdk

COPY ./build/libs/wallet-server.jar /wallet-server.jar

RUN jar -xvf wallet-server.jar && jlink --add-modules $(jdeps --recursive --multi-release 21 --ignore-missing-deps --print-module-deps -cp 'BOOT-INF/lib/*' wallet-server.jar),jdk.crypto.ec --output /java

FROM ubuntu:22.04

LABEL org.opencontainers.image.source https://github.com/attocash/wallet-server

ENV JAVA_HOME=/java
ENV PATH "${JAVA_HOME}/bin:${PATH}"

RUN useradd -m -s /bin/bash app
USER app

COPY ./build/libs/wallet-server.jar /home/atto/wallet-server.jar

COPY --from=jdk /java /java

ENTRYPOINT ["java","-XX:+UseZGC","-jar","/home/atto/wallet-server.jar"]
