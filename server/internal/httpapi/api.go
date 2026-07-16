package httpapi

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"courseaxis/server/internal/store"
)

type API struct {
	Token  string
	Store  *store.Store
	Logger *log.Logger
}
type putRequest struct {
	BaseRevision int64           `json:"baseRevision"`
	Document     json.RawMessage `json:"document"`
}

func (a API) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		jsonReply(w, http.StatusOK, map[string]any{"ok": true, "time": time.Now().UTC()})
	})
	mux.Handle("/v1/document", a.auth(http.HandlerFunc(a.document)))
	return recoverer(mux, a.Logger)
}

func (a API) auth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		supplied := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if len(supplied) != len(a.Token) || subtle.ConstantTimeCompare([]byte(supplied), []byte(a.Token)) != 1 {
			jsonReply(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (a API) document(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		jsonReply(w, http.StatusOK, a.Store.Get())
	case http.MethodPut:
		r.Body = http.MaxBytesReader(w, r.Body, 4<<20)
		defer r.Body.Close()
		decoder := json.NewDecoder(r.Body)
		decoder.DisallowUnknownFields()
		var req putRequest
		if err := decoder.Decode(&req); err != nil {
			jsonReply(w, http.StatusBadRequest, map[string]string{"error": "invalid request: " + err.Error()})
			return
		}
		if err := ensureEOF(decoder); err != nil {
			jsonReply(w, http.StatusBadRequest, map[string]string{"error": "trailing data"})
			return
		}
		env, err := a.Store.Put(req.BaseRevision, req.Document)
		if errors.Is(err, store.ErrConflict) {
			jsonReply(w, http.StatusConflict, map[string]any{"error": "revision conflict", "revision": env.Revision})
			return
		}
		if err != nil {
			jsonReply(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
			return
		}
		jsonReply(w, http.StatusOK, env)
	default:
		w.Header().Set("Allow", "GET, PUT")
		jsonReply(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
	}
}
func ensureEOF(d *json.Decoder) error {
	var x any
	if err := d.Decode(&x); err == io.EOF {
		return nil
	} else {
		return err
	}
}
func jsonReply(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
func recoverer(next http.Handler, logger *log.Logger) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				if logger != nil {
					logger.Printf("panic: %v", v)
				}
				jsonReply(w, http.StatusInternalServerError, map[string]string{"error": "internal error"})
			}
		}()
		next.ServeHTTP(w, r)
	})
}
