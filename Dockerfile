FROM scratch

ARG APPLICATION_VERSION

LABEL org.opencontainers.image.title="atto-wallet-server" \
      org.opencontainers.image.description="Atto wallet server built as a static GraalVM image" \
      org.opencontainers.image.url="https://atto.cash" \
      org.opencontainers.image.source="https://github.com/attocash/wallet-server" \
      org.opencontainers.image.version="${APPLICATION_VERSION}"

ENV APPLICATION_VERSION=${APPLICATION_VERSION}

COPY ./build/native/nativeCompile/wallet-server /app/wallet-server

WORKDIR /app

USER 65532:65532

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["/app/wallet-server"]
