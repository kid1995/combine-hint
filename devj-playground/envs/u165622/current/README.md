# JWT Test

- Local mit `curl-token.sh` einen Acceess token genetieren
- Auf den Caller ein Terminal öffen:
```sh

export ACCESS_TOKEN="TOUR_TOKEN"
no_proxy=.local curl -v u165622-service-alpine.devj-playground.svc.cluster.local:8080 -H "Authorization: Bearer $ACCESS_TOKEN"

# Futher Snippets
# no_proxy=.local curl -v u165622-service-alpine.devj-playground.svc.cluster.local:8080
# no_proxy=.local curl -v -L u165622-service-nginx.devj-playground.svc.cluster.local:8080/employee-login/auth/jwks
# no_proxy=.org curl -v https://employee.login.signal-iduna.org/auth/jwks

```
