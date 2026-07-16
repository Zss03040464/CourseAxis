package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"courseaxis/server/internal/httpapi"
	"courseaxis/server/internal/store"
)

func main() {
	token := os.Getenv("COURSEAXIS_TOKEN")
	if len(token) < 24 {
		log.Fatal("COURSEAXIS_TOKEN must contain at least 24 characters")
	}
	dir := os.Getenv("COURSEAXIS_DATA_DIR")
	if dir == "" {
		dir = "./data"
	}
	addr := os.Getenv("COURSEAXIS_ADDR")
	if addr == "" {
		addr = "127.0.0.1:8787"
	}
	st, err := store.Open(dir)
	if err != nil {
		log.Fatal(err)
	}
	logger := log.New(os.Stdout, "courseaxis-sync ", log.LstdFlags|log.LUTC)
	srv := &http.Server{Addr: addr, Handler: httpapi.API{Token: token, Store: st, Logger: logger}.Handler(), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 15 * time.Second, WriteTimeout: 20 * time.Second, IdleTimeout: 60 * time.Second}
	go func() {
		logger.Printf("listening on %s", addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatal(err)
		}
	}()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	<-ctx.Done()
	shutdown, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = srv.Shutdown(shutdown)
}
