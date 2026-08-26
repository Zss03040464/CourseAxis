FROM golang:1.27.0-alpine3.24@sha256:4c9fe60190a2a3350ddc51de80d0224b8a6698d12bdfc999fee45ea9d6c46dbc AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /courseaxis-sync ./cmd/courseaxis-sync
FROM alpine:3.24.1@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b
RUN adduser -D -H -u 10001 courseaxis && mkdir /data && chown courseaxis /data
USER courseaxis
COPY --from=build /courseaxis-sync /courseaxis-sync
ENV COURSEAXIS_ADDR=0.0.0.0:8787 COURSEAXIS_DATA_DIR=/data
EXPOSE 8787
ENTRYPOINT ["/courseaxis-sync"]
