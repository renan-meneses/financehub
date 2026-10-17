# Security status

Source has been reviewed against reachable identity/workspace paths. **Full compilation, security integration tests and dependency scans have not run**, so this increment is not cleared for deployment.

## Implemented controls

- BCrypt cost 12, minimum password length and a strict 72-byte UTF-8 ceiling; unknown-user login performs a dummy BCrypt comparison.
- Mandatory email verification before login. Random 256-bit verification, reset and refresh tokens are hashed with SHA-256 in their lookup tables.
- Access JWTs use RS256, kid, issuer, audience, expiration, issued-at, not-before and session identifier. Spring Security validates signature/time/issuer; additional validators enforce audience and required claims.
- Five-minute access token lifetime; absolute 30-day session lifetime. Access requests check live user/session state, so suspension, password changes and revocation invalidate existing access tokens.
- Refresh operations acquire the user's row lock before rereading token state. Reuse revokes the entire family; returning an empty result commits revocation before the controller returns 401.
- SameSite=Strict, HttpOnly refresh cookies, Secure by default. Disabling Secure is restricted to local/test profiles. All `/api/v1/auth/*` mutations require double-submit CSRF protection. Other protected routes authenticate only bearer headers.
- No cross-origin CORS grants. NGINX serves the frontend and API from the same origin with self-only scripts/styles and no unsafe-eval. AngularJS has CSP mode and strict dependency injection; imported/user strings use plain text bindings.
- Workspace detail lookup requires active membership, active workspace and verified/non-suspended user. Profile/session mutations scope all identifiers to the authenticated user. Authorization never trusts a client-supplied role.
- Database-backed rate limits commit independently of failed authentication. No arbitrary forwarded IP header is trusted.
- Sensitive operations append audit records in their mutation transaction. No raw tokens, passwords or financial payloads are intentionally logged.

## Operational limitations and release gates

The email outbox temporarily contains message bodies with secret links. Delete-on-delivery/expiry is implemented; protect the database, backups and SMTP connection. SMTP delivery is at least once: a crash after sending may duplicate a message, while the challenge remains single-use.

NGINX proxy traffic currently shares a backend transport-address rate bucket. Configure a trusted-proxy address policy and ingress rate limiting before a multi-user deployment; never enable unconditional trust in X-Forwarded-For. Email buckets remain per account.

Refresh is single-flight within one tab and serialized across tabs using Web Locks where supported. Clients without Web Locks must avoid simultaneous refresh requests; strict replay detection intentionally revokes on reuse rather than allowing a grace replay.

Audit triggers reject ordinary UPDATE/DELETE. Owner privileges and TRUNCATE still bypass this protection: production needs separate migration/application roles with no application DDL/TRUNCATE permission. Compose uses a development database owner and is not a production security boundary.

JWT public-key rotation is configured through the key directory; see [authentication operations](docs/AUTHENTICATION.md). Production key generation, secrets management, HTTPS enforcement at ingress, administrative provisioning/RBAC, membership administration, MFA, retention policies, monitoring access and vulnerability scans remain pending. Workspace role policies exist, but financial permission enforcement cannot be validated until those features exist.

Prometheus remains denied at the HTTP security layer. OpenAPI JSON is accessible only when explicitly enabled in the local profile. Financial/card controllers are absent, and no raw payment credentials are stored.

## References

The CSRF configuration follows Spring Security's [servlet CSRF integration](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html). JWT decoding uses its [resource-server JWT support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
