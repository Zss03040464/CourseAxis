FROM golang:1.26.5-alpine3.24@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS build
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
