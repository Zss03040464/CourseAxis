package store

import (
	"encoding/json"
	"errors"
	"testing"
)

func TestAtomicStoreAndConflict(t *testing.T) {
	s, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	doc := json.RawMessage(`{"schemaVersion":1,"events":[]}`)
	env, err := s.Put(0, doc)
	if err != nil || env.Revision != 1 {
		t.Fatalf("put: %#v %v", env, err)
	}
	if _, err = s.Put(0, doc); !errors.Is(err, ErrConflict) {
		t.Fatalf("want conflict: %v", err)
	}
	s2, err := Open(s.path[:len(s.path)-len("document.json")])
	if err != nil || s2.Get().Revision != 1 {
		t.Fatalf("reopen: %v", err)
	}
}
