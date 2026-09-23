FROM golang:1.26.5-alpine3.24@sha256:0178a641fbb4858c5f1b48e34bdaabe0350a330a1b1149aabd498d0699ff5fb2 AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /courseaxis-sync ./cmd/courseaxis-sync
FROM alpine:3.24.2@sha256:294b683cb724975bec92580e1e685676bd4b50bda910ddb8c51d4cabeaec77e6
RUN adduser -D -H -u 10001 courseaxis && mkdir /data && chown courseaxis /data
USER courseaxis
COPY --from=build /courseaxis-sync /courseaxis-sync
ENV COURSEAXIS_ADDR=0.0.0.0:8787 COURSEAXIS_DATA_DIR=/data
EXPOSE 8787
ENTRYPOINT ["/courseaxis-sync"]
