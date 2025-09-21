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
  read -p "Wie lautet der PostgreSQL Schema-Name? (leer lassen für Platzhalter): " postgres_schema_name
fi

if [[ "$use_auth_url" =~ ^(ja|j)$ ]]; then
  read -p "Bitte geben Sie die AUTH_URL ein (leer lassen für Platzhalter): " auth_url
fi


# env=dev  # For testing purposes, you can set a default value here
# use_postgres=ja  # For testing purposes, you can set a default value here
# use_kafka=ja  # For testing purposes, you can set a default value here
# use_mongodb=nein  # For testing purposes, you can set a default value here
# use_auth_url=ja  # For testing purposes, you can set a default value here
# service_name="beispiel-service"  # For testing purposes, you can set a default value here
# image_name="dev.docker.system.local/elpa-beispiel-service-tst"  # For testing purposes, you can set a default value here
# image_tag="v1.0.0"  # For testing purposes, you can set a default value here
# postgres_schema_name="beispiel_schema"  # For testing purposes, you can set a default value here
# auth_url="https://auth.example.com"  # For testing purposes, you can set a default value here


BLUEPRINT_DIR="blueprint_temp"
cp -r blueprint "$BLUEPRINT_DIR"

TARGET_DIR="envs/$env/$service_name"
mkdir -p "$TARGET_DIR"

# Fixed sed commands - process each file individually
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<env>|$env|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<service-name>|$service_name|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<image-name>|$image_name|g" {} \;
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i.bak "s|<image-tag>|$image_tag|g" {} \;

# Clean up backup files created by sed
find "$BLUEPRINT_DIR" -name "*.bak" -delete

# Build components list with proper formatting
COMPONENTS_LIST=""

if [[ "$use_postgres" =~ ^(ja|j)$ ]]; then
  if [[ -z "$COMPONENTS_LIST" ]]; then
    COMPONENTS_LIST="  - ../../base/components/postgres
  - ../components/postgres"
  else
    COMPONENTS_LIST="$COMPONENTS_LIST
  - ../../base/components/postgres
  - ../components/postgres"
  fi
fi

if [[ "$use_kafka" =~ ^(ja|j)$ ]]; then
  if [[ -z "$COMPONENTS_LIST" ]]; then
    COMPONENTS_LIST="  - ../../base/components/kafka
  - ../components/kafka"
  else
    COMPONENTS_LIST="$COMPONENTS_LIST
  - ../../base/components/kafka
  - ../components/kafka"
  fi
fi

if [[ "$use_mongodb" =~ ^(ja|j)$ ]]; then
  if [[ -z "$COMPONENTS_LIST" ]]; then
    COMPONENTS_LIST="  - ../../base/components/mongo
  - ../components/mongo"
  else
    COMPONENTS_LIST="$COMPONENTS_LIST
  - ../../base/components/mongo
  - ../components/mongo"
  fi
fi

# Build literals list with proper formatting
LITERALS_LIST="      - SERVICE_NAME=$service_name"

if [[ "$use_postgres" =~ ^(ja|j)$ ]]; then
  if [[ -n "$postgres_schema_name" ]]; then
    LITERALS_LIST="$LITERALS_LIST
      - POSTGRES_SCHEMA_NAME=$postgres_schema_name"
  else
    LITERALS_LIST="$LITERALS_LIST
      - POSTGRES_SCHEMA_NAME=<postgres-schema-name>"
  fi
fi

echo "after POSTGRES_SCHEMA_NAME $LITERALS_LIST"

if [[ "$use_auth_url" =~ ^(ja|j)$ ]]; then
  if [[ -n "$auth_url" ]]; then
    LITERALS_LIST="$LITERALS_LIST
      - AUTH_URL=$auth_url"
  else
    LITERALS_LIST="$LITERALS_LIST
      - AUTH_URL=<auth-url>"
  fi
fi

echo "after auth_url $LITERALS_LIST"

# Process kustomization.yaml with proper YAML formatting
if [[ -f "$BLUEPRINT_DIR/kustomization.yaml" ]]; then
  # Create a temporary file for processing
  temp_file=$(mktemp)
  
  # Process the file line by line
  while IFS= read -r line; do
    if [[ "$line" == *"<components-list>"* ]]; then
      # If we have components, replace the placeholder with the components list
      if [[ -n "$COMPONENTS_LIST" ]]; then
        echo "$COMPONENTS_LIST"
      else
        # If no components, skip this line (remove the placeholder)
        continue
      fi
    elif [[ "$line" == *"<literals-list>"* ]]; then
      if [[ -n "$LITERALS_LIST" ]]; then
        echo "$LITERALS_LIST"
      else
        # If no literals, skip this line (remove the placeholder)
        continue
      fi
    else
      # Keep the line as-is
      echo "$line"
    fi
  done < "$BLUEPRINT_DIR/kustomization.yaml" > "$temp_file"
  
  # Move the processed file back
  mv "$temp_file" "$BLUEPRINT_DIR/kustomization.yaml"
  
  # Handle image registry placeholder
  sed -i "s|<image-name>|$image_name|g" "$BLUEPRINT_DIR/kustomization.yaml"
fi

# Clean up any remaining backup files
find "$BLUEPRINT_DIR" -name "*.bak" -delete

# Move files to target directory
mv "$BLUEPRINT_DIR"/* "$TARGET_DIR"
rmdir "$BLUEPRINT_DIR"

echo
echo "✓ Der neue Service '$service_name' wurde erfolgreich im Verzeichnis '$TARGET_DIR' erstellt."
echo
echo "Komponenten:"
if [[ -n "$COMPONENTS_LIST" ]]; then
  echo "$COMPONENTS_LIST" | sed 's/^/  /'
else
  echo "  (keine)"
fi
echo
echo "Konfiguration:"
echo "$LITERALS_LIST" | sed 's/^/  /'