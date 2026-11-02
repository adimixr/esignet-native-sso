package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

const (
	clientID      = "native-sso-superapp"
	clientKeyID   = "0L9pwn4iNkfC4r5QP-aFOQ"
	assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
	maxBodyBytes  = 4096
)

type signer struct {
	key              *rsa.PrivateKey
	allowedAudiences map[string]bool
	requests         atomic.Uint64
}

type assertionRequest struct {
	Audience string `json:"audience"`
}
type assertionResponse struct {
	ClientAssertion     string `json:"client_assertion"`
	ClientAssertionType string `json:"client_assertion_type"`
}

func main() {
	keyPath := env("NATIVE_SSO_CLIENT_KEY", "client-private.pem")
	key, err := loadRSAKey(keyPath)
	if err != nil {
		log.Fatalf("load RSA client key %q: %v; copy your registered client-private.pem here or set NATIVE_SSO_CLIENT_KEY", keyPath, err)
	}

	base := strings.TrimRight(env("ESIGNET_BASE_URL", "http://127.0.0.1:8081"), "/")
	audiences := map[string]bool{
		base + "/oauth2/par":   true,
		base + "/oauth2/token": true,
		// Some eSignet deployments validate against the issuer rather than the endpoint.
		base: true,
	}
	// The emulator reaches the host as 10.0.2.2. Accept only these exact local-demo audience forms.
	// These exact alternatives cover the common issuer hostnames used by the local
	// Docker setup and Android emulator. No arbitrary audience is ever signed.
	for _, host := range []string{"127.0.0.1", "localhost", "10.0.2.2"} {
		localBase := strings.Replace(base, "127.0.0.1", host, 1)
		localBase = strings.Replace(localBase, "localhost", host, 1)
		localBase = strings.Replace(localBase, "10.0.2.2", host, 1)
		audiences[localBase+"/oauth2/par"] = true
		audiences[localBase+"/oauth2/token"] = true
		audiences[localBase] = true
	}

	s := &signer{key: key, allowedAudiences: audiences}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok", "client_id": clientID})
	})
	mux.HandleFunc("POST /assertion", s.handleAssertion)
	addr := env("NATIVE_SSO_SIGNER_ADDR", "127.0.0.1:8090")
	log.Printf("Native SSO local signer listening on %s; client=%s; key path=%s", addr, clientID, keyPath)
	log.Printf("Development only: this service exposes a constrained signing endpoint; do not expose it to a public network.")
	server := &http.Server{Addr: addr, Handler: localOnly(mux), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 10 * time.Second, IdleTimeout: 30 * time.Second}
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
}

func (s *signer) handleAssertion(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("Content-Type") == "" || !strings.HasPrefix(strings.ToLower(r.Header.Get("Content-Type")), "application/json") {
		http.Error(w, `{"error":"content_type_must_be_application_json"}`, http.StatusUnsupportedMediaType)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	defer r.Body.Close()
	var req assertionRequest
	dec := json.NewDecoder(r.Body)
	if err := dec.Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid_json"}`, http.StatusBadRequest)
		return
	}
	if req.Audience == "" || !s.allowedAudiences[req.Audience] {
		http.Error(w, `{"error":"audience_not_allowed"}`, http.StatusBadRequest)
		return
	}
	// Short-lived, unique assertions; the private key never leaves this process.
	now := time.Now().Unix()
	claims := map[string]any{"iss": clientID, "sub": clientID, "aud": req.Audience, "jti": randomJTI(), "iat": now, "exp": now + 120}
	jwt, err := signRS256(s.key, map[string]any{"alg": "RS256", "typ": "JWT", "kid": clientKeyID}, claims)
	if err != nil {
		log.Printf("assertion signing failed: %v", err)
		http.Error(w, `{"error":"signing_failed"}`, http.StatusInternalServerError)
		return
	}
	s.requests.Add(1)
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, assertionResponse{ClientAssertion: jwt, ClientAssertionType: assertionType})
}

func localOnly(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		ip := net.ParseIP(host)
		if ip == nil || (!ip.IsLoopback() && !ip.IsPrivate()) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		w.Header().Set("X-Content-Type-Options", "nosniff")
		next.ServeHTTP(w, r)
	})
}

func loadRSAKey(path string) (*rsa.PrivateKey, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	block, _ := pem.Decode(b)
	if block == nil {
		return nil, errors.New("no PEM block found")
	}
	if k, err := x509.ParsePKCS8PrivateKey(block.Bytes); err == nil {
		rsaKey, ok := k.(*rsa.PrivateKey)
		if !ok {
			return nil, fmt.Errorf("key is %T, expected RSA", k)
		}
		if err := rsaKey.Validate(); err != nil {
			return nil, err
		}
		return rsaKey, nil
	}
	k, err := x509.ParsePKCS1PrivateKey(block.Bytes)
	if err != nil {
		return nil, err
	}
	if err := k.Validate(); err != nil {
		return nil, err
	}
	return k, nil
}

func signRS256(key *rsa.PrivateKey, header, claims map[string]any) (string, error) {
	h, err := json.Marshal(header)
	if err != nil {
		return "", err
	}
	p, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	input := b64url(h) + "." + b64url(p)
	digest := sha256.Sum256([]byte(input))
	sig, err := rsa.SignPKCS1v15(rand.Reader, key, crypto.SHA256, digest[:])
	if err != nil {
		return "", err
	}
	return input + "." + b64url(sig), nil
}
func randomJTI() string {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return fmt.Sprintf("fallback-%d", time.Now().UnixNano())
	}
	return b64url(b)
}
func b64url(b []byte) string { return base64.RawURLEncoding.EncodeToString(b) }
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
func env(k, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(k)); v != "" {
		return v
	}
	return fallback
}
