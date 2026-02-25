#!/bin/bash
#
#
# Benötigt `kubectl` und `kubeseal`.
#
# Datei `public-key-nop` muss im aktuellen Verzeichnis liegen.
#

NAMESPACE=elpa-elpa4
ENV=dev

PASSWORD_OWNER=""

echo "enter your owner password:"
read -r PASSWORD_OWNER # technische-user

# Function to base64 encode values
encode_value() {
    echo -n "$1" | base64 -w 0
}

echo "${PASSWORD_OWNER} user password:"
read -r PW_INPUT

# Create a temporary YAML file with base64 encoded values
cat > "/tmp/${ENV}-${PASSWORD_OWNER}-secret.yaml" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${ENV}-${PASSWORD_OWNER}-secret
  namespace: ${NAMESPACE}
  annotations:
    sealedsecrets.bitnami.com/managed: "true"
type: Opaque
data:
  PASSWORD: $(encode_value "$PW_INPUT")
EOF

# Seal the secret
kubeseal --format yaml --cert=public-key-nop < "/tmp/${ENV}-${PASSWORD_OWNER}-secret.yaml" > "${ENV}-${PASSWORD_OWNER}-secret.yaml"

# Clean up temporary file
rm "/tmp/${ENV}-${PASSWORD_OWNER}-secret.yaml"

echo "Sealed secrets generated successfully!"

