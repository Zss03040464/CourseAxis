package store

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

type Envelope struct {
	Revision int64           `json:"revision"`
	Document json.RawMessage `json:"document,omitempty"`
}

type Store struct {
	mu   sync.RWMutex
	path string
	env  Envelope
}

func Open(dir string) (*Store, error) {
	if dir == "" {
		return nil, errors.New("empty data directory")
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, fmt.Errorf("create data directory: %w", err)
	}
	s := &Store{path: filepath.Join(dir, "document.json")}
	data, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read store: %w", err)
	}
	if err := json.Unmarshal(data, &s.env); err != nil {
		return nil, fmt.Errorf("decode store: %w", err)
	}
	if s.env.Revision < 0 {
		return nil, errors.New("negative revision")
	}
	return s, nil
}

func (s *Store) Get() Envelope {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return Envelope{Revision: s.env.Revision, Document: append(json.RawMessage(nil), s.env.Document...)}
}

var ErrConflict = errors.New("revision conflict")

func (s *Store) Put(base int64, document json.RawMessage) (Envelope, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if base != s.env.Revision {
		return Envelope{Revision: s.env.Revision}, ErrConflict
	}
	if len(document) == 0 || !json.Valid(document) {
		return Envelope{}, errors.New("invalid document JSON")
	}
	var version struct {
		SchemaVersion int `json:"schemaVersion"`
	}
	if err := json.Unmarshal(document, &version); err != nil || version.SchemaVersion != 1 {
		return Envelope{}, errors.New("unsupported or missing schemaVersion")
	}
	next := Envelope{Revision: s.env.Revision + 1, Document: append(json.RawMessage(nil), document...)}
	if err := s.writeAtomic(next); err != nil {
		return Envelope{}, err
	}
	s.env = next
	return s.GetUnsafe(), nil
}

func (s *Store) GetUnsafe() Envelope {
	return Envelope{Revision: s.env.Revision, Document: append(json.RawMessage(nil), s.env.Document...)}
}

func (s *Store) writeAtomic(env Envelope) error {
	data, err := json.MarshalIndent(env, "", "  ")
	if err != nil {
		return fmt.Errorf("encode store: %w", err)
	}
	temp, err := os.CreateTemp(filepath.Dir(s.path), ".courseaxis-*.tmp")
	if err != nil {
		return fmt.Errorf("create temp: %w", err)
	}
	name := temp.Name()
	cleanup := func() { _ = temp.Close(); _ = os.Remove(name) }
	if err := temp.Chmod(0o600); err != nil {
		cleanup()
		return err
	}
	if _, err := temp.Write(data); err != nil {
		cleanup()
		return err
	}
	if err := temp.Sync(); err != nil {
		cleanup()
		return err
	}
	if err := temp.Close(); err != nil {
		_ = os.Remove(name)
		return err
	}
	if err := os.Rename(name, s.path); err != nil {
		_ = os.Remove(name)
		return fmt.Errorf("rename store: %w", err)
	}
	if dir, err := os.Open(filepath.Dir(s.path)); err == nil {
		_ = dir.Sync()
		_ = dir.Close()
	}
	return nil
}
