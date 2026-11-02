package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestSignRS256ProducesVerifiableJWT(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	jwt, err := signRS256(key, map[string]any{"alg": "RS256", "typ": "JWT", "kid": clientKeyID}, map[string]any{"iss": clientID, "sub": clientID, "aud": "http://10.0.2.2:8081/oauth2/par"})
	if err != nil {
		t.Fatal(err)
	}
	parts := strings.Split(jwt, ".")
	if len(parts) != 3 {
		t.Fatalf("expected 3 JWT parts, got %d", len(parts))
	}
	input := parts[0] + "." + parts[1]
	digest := sha256.Sum256([]byte(input))
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		t.Fatal(err)
	}
	if err := rsa.VerifyPKCS1v15(&key.PublicKey, crypto.SHA256, digest[:], sig); err != nil {
		t.Fatalf("signature verification failed: %v", err)
	}
	var claims map[string]any
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(payload, &claims); err != nil {
		t.Fatal(err)
	}
	if claims["iss"] != clientID || claims["sub"] != clientID {
		t.Fatalf("unexpected claims: %#v", claims)
	}
}

func TestAssertionRejectsUnapprovedAudience(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	s := &signer{key: key, allowedAudiences: map[string]bool{"http://10.0.2.2:8081/oauth2/par": true}}
	req := httptest.NewRequest("POST", "/assertion", strings.NewReader(`{"audience":"https://attacker.example/"}`))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	s.handleAssertion(w, req)
	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}
