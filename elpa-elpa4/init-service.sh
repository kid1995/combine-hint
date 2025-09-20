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

find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i "s|<env>|$env|g" {} +
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i "s|<service-name>|$service_name|g" {} +
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i "s|<image-name>|$image_name|g" {} +
find "$BLUEPRINT_DIR" -type f -name "*.yaml" -exec sed -i "s|<image-tag>|$image_tag|g" {} +

# Anpassen der Kustomization-Datei für Komponenten und Literale
COMPONENTS=""
LITERALS=""

if [[ "$use_postgres" =~ ^(ja|j)$ ]]; then
  COMPONENTS+="\n  - ../../base/components/postgres\n  - ../components/postgres"
  LITERALS+="\n    - POSTGRES_SCHEMA=$postgres_schema_name"
fi

if [[ "$use_kafka" =~ ^(ja|j)$ ]]; then
  COMPONENTS+="\n  - ../../base/components/kafka\n  - ../components/kafka"
fi

# Hinweis: MongoDB hat in den bereitgestellten Beispielen keine `kustomization.yaml` im `dev`-Verzeichnis, daher wird nur der Basispfad hinzugefügt.
if [[ "$use_mongodb" =~ ^(ja|j)$ ]]; then
  COMPONENTS+="\n  - ../../base/components/mongodb"
fi

if [[ "$use_auth_url" =~ ^(ja|j)$ ]]; then
  LITERALS+="\n    - AUTH_URL=$auth_url"
fi

# Ersetzen der Platzhalter in den YAML-Dateien
find "$BLUEPRINT_DIR" -type f -name "kustomization.yaml" -exec sed -i "/<component-list>/c$COMPONENTS" {} +
find "$BLUEPRINT_DIR" -type f -name "kustomization.yaml" -exec sed -i "/<literal-list>/c$LITERALS" {} +

mv "$BLUEPRINT_DIR"/* "$TARGET_DIR"
rmdir "$BLUEPRINT_DIR"

echo "Der neue Service '$service_name' wurde erfolgreich im Verzeichnis '$TARGET_DIR' erstellt."