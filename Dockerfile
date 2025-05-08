FROM scratch

COPY ./build/native/nativeCompile/wallet-server /app/wallet-server

WORKDIR /app

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["./wallet-server"]
