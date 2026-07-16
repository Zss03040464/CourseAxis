package httpapi

import (
	"bytes"
	"courseaxis/server/internal/store"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAPI(t *testing.T) {
	st, _ := store.Open(t.TempDir())
	h := API{Token: "123456789012345678901234", Store: st}.Handler()
	req := httptest.NewRequest(http.MethodGet, "/v1/document", nil)
	rr := httptest.NewRecorder()
	h.ServeHTTP(rr, req)
	if rr.Code != 401 {
		t.Fatal(rr.Code)
	}
	payload := []byte(`{"baseRevision":0,"document":{"schemaVersion":1,"events":[]}}`)
	req = httptest.NewRequest(http.MethodPut, "/v1/document", bytes.NewReader(payload))
	req.Header.Set("Authorization", "Bearer 123456789012345678901234")
	rr = httptest.NewRecorder()
	h.ServeHTTP(rr, req)
	if rr.Code != 200 {
		t.Fatalf("%d %s", rr.Code, rr.Body.String())
	}
	var env store.Envelope
	if err := json.Unmarshal(rr.Body.Bytes(), &env); err != nil || env.Revision != 1 {
		t.Fatal(err, env)
	}
}
