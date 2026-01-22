#!/bin/bash
#
#
# Benötigt `kubectl` und `kubeseal`.
#
# Datei `public-key-nop` muss im aktuellen Verzeichnis liegen.
#

NAMESPACE=elpa-elpa4
ENV=dev

# Function to base64 encode values
encode_value() {
    echo -n "$1" | base64 -w 0
}
#
# postgresuser-secrets
#

echo "PostgreSQL user password:"
read -r POSTGRESUSER_PW

# Create a temporary YAML file with base64 encoded values
cat > "/tmp/${ENV}-postgresuser-secret.yaml" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${ENV}-postgresuser-secret
  namespace: ${NAMESPACE}
  annotations:
    sealedsecrets.bitnami.com/managed: "true"
type: Opaque
data:
  PASSWORD: $(encode_value "$POSTGRESUSER_PW")
EOF

# Seal the secret
kubeseal --format yaml --cert=public-key-nop < "/tmp/${ENV}-postgresuser-secret.yaml" > "${ENV}-postgresuser-secret.yaml"

# Clean up temporary file
rm "/tmp/${ENV}-postgresuser-secret.yaml"

echo "Sealed secrets generated successfully!"

