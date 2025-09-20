#!/bin/bash

echo "In welcher Umgebung soll der Service bereitgestellt werden? (Bitte Option Nummer eingeben)"
select env in "dev" "abn" "prod"; do
    if [ -n "$env" ]; then
        echo "Sie haben die Umgebung '$env' ausgewählt."
        break
    else
        echo "Ungültige Auswahl. Bitte wählen Sie eine der Optionen."
    fi
done

echo
read -p "Wird PostgreSQL verwendet? (ja/nein): " use_postgres
read -p "Wird Kafka verwendet? (ja/nein): " use_kafka
read -p "Wird MongoDB verwendet? (ja/nein): " use_mongodb
read -p "Wird eine AUTH_URL benötigt? (ja/nein): " use_auth_url

echo
read -p "Wie soll der neue Service heißen? (z.B. 'neuer-service'): " service_name
read -p "Wie lautet der Name des Docker-Images?: " image_name
read -p "Welchen Image-Tag soll das Image haben? (z.B. 'v1.0.0'): " image_tag

if [[ "$use_postgres" =~ ^(ja|j)$ ]]; then
  read -p "Wie lautet der PostgreSQL Schema-Name?: " postgres_schema_name
fi

if [[ "$use_auth_url" =~ ^(ja|j)$ ]]; then
  read -p "Bitte geben Sie die AUTH_URL ein: " auth_url
fi

BLUEPRINT_DIR="blueprint_temp"
cp -r blueprint "$BLUEPRINT_DIR"

TARGET_DIR="envs/$env/$service_name"
mkdir -p "$TARGET_DIR"

# Fixed sed commands - process each file individually instead of using find with +
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<env>|$env|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<service-name>|$service_name|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<image-name>|$image_name|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<image-tag>|$image_tag|g" {} \;

# Clean up backup files created by sed
find "$BLUEPRINT_DIR" -name "*.bak" -delete

# Anpassen der Kustomization-Datei für Komponenten und Literale
COMPONENTS=""
LITERALS=""

if [[ "$use_postgres" =~ ^(ja|j)$ ]]; then
  COMPONENTS+="  - ../../base/components/postgres\n  - ../components/postgres"
  if [[ -n "$postgres_schema_name" ]]; then
    LITERALS+="      - POSTGRES_SCHEMA_NAME=$postgres_schema_name"
  fi
fi

if [[ "$use_kafka" =~ ^(ja|j)$ ]]; then
  if [[ -n "$COMPONENTS" ]]; then
    COMPONENTS+="\n"
  fi
  COMPONENTS+="  - ../../base/components/kafka\n  - ../components/kafka"
fi

if [[ "$use_mongodb" =~ ^(ja|j)$ ]]; then
  if [[ -n "$COMPONENTS" ]]; then
    COMPONENTS+="\n"
  fi
  COMPONENTS+="  - ../../base/components/mongo\n  - ../components/mongo"
fi

if [[ "$use_auth_url" =~ ^(ja|j)$ ]]; then
  if [[ -n "$LITERALS" ]]; then
    LITERALS+="\n"
  fi
  LITERALS+="      - AUTH_URL=$auth_url"
fi

# Handle the case where no postgres schema is provided but postgres is used
if [[ "$use_postgres" =~ ^(ja|j)$ ]] && [[ -z "$postgres_schema_name" ]]; then
  if [[ -n "$LITERALS" ]]; then
    LITERALS+="\n"
  fi
  LITERALS+="      - POSTGRES_SCHEMA_NAME=<postgres-schema-name>"
fi

# Replace placeholders in kustomization.yaml
if [[ -f "$BLUEPRINT_DIR/kustomization.yaml" ]]; then
  if [[ -n "$COMPONENTS" ]]; then
    # Use printf to handle newlines properly and escape for sed
    COMPONENTS_ESCAPED=$(printf "%s" "$COMPONENTS" | sed 's/[[\.*^$()+?{|]/\\&/g')
    sed -i.bak "s|<components-list>|$COMPONENTS_ESCAPED|g" "$BLUEPRINT_DIR/kustomization.yaml"
  else
    # Remove the components-list placeholder if no components are needed
    sed -i.bak "/<components-list>/d" "$BLUEPRINT_DIR/kustomization.yaml"
  fi
  
  if [[ -n "$LITERALS" ]]; then
    # Add SERVICE_NAME literal first, then any additional literals
    FULL_LITERALS="      - SERVICE_NAME=$service_name"
    if [[ -n "$LITERALS" ]]; then
      FULL_LITERALS+="\n$LITERALS"
    fi
    LITERALS_ESCAPED=$(printf "%s" "$FULL_LITERALS" | sed 's/[[\.*^$()+?{|]/\\&/g')
    sed -i.bak "s|      - SERVICE_NAME=<service-name>|$LITERALS_ESCAPED|g" "$BLUEPRINT_DIR/kustomization.yaml"
  else
    # Just replace the service name placeholder
    sed -i.bak "s|<service-name>|$service_name|g" "$BLUEPRINT_DIR/kustomization.yaml"
  fi
  
  # Handle image registry placeholder - set a default value
  sed -i.bak "s|<image-registry>/<project-name>|dev.docker.system.local/elpa-$service_name-tst|g" "$BLUEPRINT_DIR/kustomization.yaml"
  
  # Clean up backup file
  rm -f "$BLUEPRINT_DIR/kustomization.yaml.bak"
fi

# Clean up any remaining backup files
find "$BLUEPRINT_DIR" -name "*.bak" -delete

mv "$BLUEPRINT_DIR"/* "$TARGET_DIR"
rmdir "$BLUEPRINT_DIR"

echo "Der neue Service '$service_name' wurde erfolgreich im Verzeichnis '$TARGET_DIR' erstellt."