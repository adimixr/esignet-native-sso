// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package thunderidengine

import (
	"context"
	"crypto/ecdsa"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	oauthconfig "github.com/thunder-id/thunderid/internal/oauth/config"
	"github.com/thunder-id/thunderid/internal/system/jose/jwe"
	"github.com/thunder-id/thunderid/internal/system/jose/jwt"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

const deviceSecretValidity = int64(30 * 24 * 60 * 60)

// nativeDeviceSecretProvider is the default stateless Native SSO provider. The device secret is a
// signed JWT wrapped in a server-encrypted JWE. The JWE protects the signed assertion at rest and
// during app-to-app handoff; the inner JWS is what the AS validates cryptographically.
type nativeDeviceSecretProvider struct {
	jwtService    jwt.JWTServiceInterface
	jweService    jwe.JWEServiceInterface
	runtimeCrypto providers.RuntimeCryptoProvider
	issuer        string
	keyID         string
}

// NewDeviceSecretProvider returns the default stateless Native SSO provider.
func NewDeviceSecretProvider(

	jwtService jwt.JWTServiceInterface,
	jweService jwe.JWEServiceInterface,
	runtimeCrypto providers.RuntimeCryptoProvider,
	oauthCfg oauthconfig.Config,
) providers.DeviceSecretProvider {
	return &nativeDeviceSecretProvider{
		jwtService:    jwtService,
		jweService:    jweService,
		runtimeCrypto: runtimeCrypto,
		issuer:        oauthCfg.JWT.Issuer,
		keyID:         oauthCfg.JWT.PreferredKeyID,
	}
}

func (p *nativeDeviceSecretProvider) GenerateDeviceSecret(
	ctx context.Context, input *providers.DeviceSecretInput,
) (string, *providers.DeviceSecretClaims, *common.ServiceError) {
	if input == nil || input.ClientID == "" || input.Subject == "" {
		return "", nil, &common.InternalServerError
	}

	now := time.Now().Unix()
	exp := now + deviceSecretValidity
	claims := map[string]interface{}{
		"sub":       input.Subject,
		"iss":       p.issuer,
		"aud":       input.ClientID,
		"iat":       now,
		"exp":       exp,
		"client_id": input.ClientID,
		"auth_id":   input.AuthID,
		"scope":     strings.Join(input.Scopes, " "),
		"acr":       input.ACR,
		"claims":    input.Claims,
		"cnf":       map[string]interface{}{"jkt": input.CnfJkt},
		"sid":       input.SID,
	}
	signed, _, err := p.jwtService.GenerateJWT(ctx, input.Subject, p.issuer, deviceSecretValidity,
		claims, jwt.TokenTypeJWT, "")
	if err != nil {
		return "", nil, err
	}

	// ds_hash is intentionally a server-defined binding to the inner signed value. This avoids
	// circularity: the hash is placed in the ID token, while the device secret itself is the
	// encrypted wrapper containing this signed JWT.
	dsHash := hashDeviceSecretValue(signed)

	keys, errGo := p.runtimeCrypto.GetPublicKeys(ctx, providers.PublicKeyFilter{KeyID: p.keyID})
	if errGo != nil || len(keys) == 0 {
		return "", nil, &common.InternalServerError
	}
	recipient := &providers.KeyRef{PublicKey: keys[0].PublicKey, KeyID: keys[0].KeyID}
	keyAlg := ""
	switch keys[0].PublicKey.(type) {
	case *rsa.PublicKey:
		keyAlg = "RSA-OAEP-256"
	case *ecdsa.PublicKey:
		keyAlg = "ECDH-ES"
	default:
		return "", nil, &common.InternalServerError
	}
	encrypted, svcErr := p.jweService.Encrypt(ctx, []byte(signed), recipient,
		keyAlg, jwe.ContentEncAlgorithm("A256GCM"), "JWT", keys[0].Kid)
	if svcErr != nil {
		return "", nil, svcErr
	}

	return encrypted, &providers.DeviceSecretClaims{
		Subject: input.Subject, Issuer: p.issuer, ClientID: input.ClientID, AuthID: input.AuthID,
		Scopes: append([]string(nil), input.Scopes...), ACR: input.ACR, Claims: input.Claims,
		CnfJkt: input.CnfJkt, DSHash: dsHash, SID: input.SID, ExpiresAt: exp,
	}, nil
}

func (p *nativeDeviceSecretProvider) ValidateDeviceSecret(
	ctx context.Context, deviceSecret string, clientID string,
) (*providers.DeviceSecretClaims, *common.ServiceError) {
	if deviceSecret == "" || clientID == "" {
		return nil, &common.InternalServerError
	}
	plaintext, svcErr := p.jweService.Decrypt(ctx, deviceSecret)
	if svcErr != nil {
		return nil, svcErr
	}
	inner := string(plaintext)
	if err := p.jwtService.VerifyJWT(ctx, inner, clientID, p.issuer); err != nil {
		return nil, err
	}
	claims, _, err := jwt.DecodeJWT(inner)
	if err != nil {
		return nil, &common.InternalServerError
	}
	out, err := parseDeviceSecretClaims(claims)
	if err != nil || out.ClientID != clientID || out.ExpiresAt <= time.Now().Unix() {
		return nil, &common.InternalServerError
	}
	out.DSHash = hashDeviceSecretValue(inner)
	return out, nil
}

func parseDeviceSecretClaims(c map[string]interface{}) (*providers.DeviceSecretClaims, error) {
	sub, ok1 := c["sub"].(string)
	iss, ok2 := c["iss"].(string)
	clientID, ok3 := c["client_id"].(string)
	authID, ok4 := c["auth_id"].(string)
	acr, _ := c["acr"].(string)
	sid, _ := c["sid"].(string)
	exp, ok5 := numberClaim(c["exp"])
	if !(ok1 && ok2 && ok3 && ok4 && ok5) {
		return nil, fmt.Errorf("invalid device secret claims")
	}
	scopes := scopeClaim(c["scope"])
	var cnfJkt string
	if cnf, ok := c["cnf"].(map[string]interface{}); ok {
		cnfJkt, _ = cnf["jkt"].(string)
	}
	claims, _ := c["claims"].(map[string]interface{})
	return &providers.DeviceSecretClaims{
		Subject: sub, Issuer: iss, ClientID: clientID, AuthID: authID, Scopes: scopes,
		ACR: acr, Claims: claims, CnfJkt: cnfJkt, SID: sid, ExpiresAt: exp,
	}, nil
}

func numberClaim(v interface{}) (int64, bool) {
	switch n := v.(type) {
	case float64:
		return int64(n), true
	case int64:
		return n, true
	case json.Number:
		x, err := n.Int64()
		return x, err == nil
	default:
		return 0, false
	}
}

func scopeClaim(v interface{}) []string {
	switch x := v.(type) {
	case string:
		if strings.TrimSpace(x) == "" {
			return []string{}
		}
		return strings.Fields(x)
	case []interface{}:
		out := make([]string, 0, len(x))
		for _, item := range x {
			if s, ok := item.(string); ok {
				out = append(out, s)
			}
		}
		return out
	default:
		return []string{}
	}
}
func hashDeviceSecretValue(value string) string {
	sum := sha256.Sum256([]byte(value))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}
