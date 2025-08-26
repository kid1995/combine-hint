#!/bin/bash

#
# Erzeugt die Dateien `my-cluster-name-secrets.yaml` und `mongouser-secret.yaml`.
#
# Benötigt `kubectl` und `kubeseal`.
#
# Datei `public-key-nop` muss im aktuellen Verzeichnis liegen.
#

NAMESPACE=elpa-elpa4

echo "Environments:"
read -r ENV

echo "MongoDB Backup password:"
read -r BACKUP_PW

echo "MongoDB Database Admin password:"
read -r DB_ADMIN_PW

echo "MongoDB Cluster Admin password:"
read -r CLUSTER_ADMIN_PW

echo "MongoDB Cluster Monitor password:"
read -r CLUSTER_MONITOR_PW

# Function to base64 encode values
encode_value() {
    echo -n "$1" | base64 -w 0
}

#
# my-cluster-name-secrets
#

# Create a temporary YAML file with base64 encoded values
cat > "/tmp/${ENV}-elpa-elpa4-secrets.yaml" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${ENV}-elpa-elpa4-secrets
  namespace: ${NAMESPACE}
type: Opaque
data:
  MONGODB_BACKUP_USER: $(encode_value "backup")
  MONGODB_BACKUP_PASSWORD: $(encode_value "$BACKUP_PW")
  MONGODB_DATABASE_ADMIN_USER: $(encode_value "databaseAdmin")
  MONGODB_DATABASE_ADMIN_PASSWORD: $(encode_value "$DB_ADMIN_PW")
  MONGODB_CLUSTER_ADMIN_USER: $(encode_value "clusterAdmin")
  MONGODB_CLUSTER_ADMIN_PASSWORD: $(encode_value "$CLUSTER_ADMIN_PW")
  MONGODB_CLUSTER_MONITOR_USER: $(encode_value "clusterMonitor")
  MONGODB_CLUSTER_MONITOR_PASSWORD: $(encode_value "$CLUSTER_MONITOR_PW")
  MONGODB_USER_ADMIN_USER: $(encode_value "userAdmin")  
EOF

# Seal the secret
kubeseal --format yaml --cert=public-key-nop < "/tmp/${ENV}-elpa-elpa4-secrets.yaml" > "${ENV}-elpa-elpa4-secrets.yaml"

# Clean up temporary file
rm "/tmp/${ENV}-elpa-elpa4-secrets.yaml"

#
# mongouser-secret
#

echo "MongoDB mongouser password:"
read -r MONGOUSER_PW

# Create a temporary YAML file with base64 encoded values
cat > "/tmp/${ENV}-mongouser-secret.yaml" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${ENV}-mongouser-secret
  namespace: ${NAMESPACE}
type: Opaque
data:
  PASSWORD: $(encode_value "$MONGOUSER_PW")
EOF

# Seal the secret
kubeseal --format yaml --cert=public-key-nop < "/tmp/${ENV}-mongouser-secret.yaml" > "${ENV}-mongouser-secret.yaml"

# Clean up temporary file
rm "/tmp/${ENV}-mongouser-secret.yaml"

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
type: Opaque
data:
  PASSWORD: $(encode_value "$POSTGRESUSER_PW")
EOF

# Seal the secret
kubeseal --format yaml --cert=public-key-nop < "/tmp/${ENV}-postgresuser-secret.yaml" > "${ENV}-postgresuser-secret.yaml"

# Clean up temporary file
rm "/tmp/${ENV}-postgresuser-secret.yaml"

echo "Sealed secrets generated successfully!"

