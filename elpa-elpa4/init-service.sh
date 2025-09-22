#!/bin/bash

set -e  # Exit on error

# Configuration
BLUEPRINT_DIR="blueprint_temp"
ENVS_DIR="envs"

# Helper Functions
prompt_yes_no() {
    local prompt="$1"
    local response

    while true; do
        read -p "$prompt (ja/nein - j/n): " response
        
        # Check if the input is empty (user just pressed Enter)
        if [[ -z "$response" ]]; then
            echo "Keine Eingabe. 'nein' wurde angenommen."
            return 1 # Return 1 (false)
        fi

        # Convert the input to lowercase for case-insensitivity
        response=$(echo "$response" | tr '[:upper:]' '[:lower:]')

        # Check for valid "yes" or "no" input
        if [[ "$response" =~ ^(ja|j|true|y)$ ]]; then
            return 0 # Return 0 (true)
        elif [[ "$response" =~ ^(nein|ne|n|false|jein|jain)$ ]]; then
            return 1 # Return 1 (false)
        else
            echo "Ungültige Eingabe. Bitte geben Sie 'ja' oder 'nein' ein."            
        fi
    done
}

prompt_value() {
    local prompt="$1"
    local var_name="$2"
    local default="$3"
    local value
    
    read -p "$prompt" value
    if [[ -z "$value" && -n "$default" ]]; then
        value="$default"
    fi
    eval "$var_name='$value'"
}

select_environment() {
    echo "In welcher Umgebung soll der Service bereitgestellt werden? (Bitte Option Nummer eingeben)"
    select env in "dev" "abn" "prod"; do
        if [ -n "$env" ]; then
            echo "Sie haben die Umgebung '$env' ausgewählt."
            break
        else
            echo "Ungültige Auswahl. Bitte wählen Sie eine der Optionen."
        fi
    done
}

gather_requirements() {
    echo
    prompt_yes_no "Wird PostgreSQL verwendet?" && use_postgres=true || use_postgres=false
    prompt_yes_no "Wird Kafka verwendet?" && use_kafka=true || use_kafka=false
    prompt_yes_no "Wird MongoDB verwendet?" && use_mongodb=true || use_mongodb=false
    prompt_yes_no "Wird eine AUTH_URL benötigt?" && use_auth_url=true || use_auth_url=false
}

gather_service_details() {
    echo
    prompt_value "Wie soll der neue Service heißen? (z.B. 'neuer-service'): " service_name
    prompt_value "Wie lautet der Name des Docker-Images?: " image_name
    prompt_value "Welchen Image-Tag soll das Image haben? (z.B. 'v1.0.0'): " image_tag
    
    if $use_postgres; then
        prompt_value "Wie lautet der PostgreSQL Schema-Name? (leer lassen für Platzhalter): " postgres_schema_name "<postgres-schema-name>"
    fi
    
    if $use_auth_url; then
        prompt_value "Bitte geben Sie die AUTH_URL ein (leer lassen für Platzhalter): " auth_url "<auth-url>"
    fi
}

prepare_blueprint() {
    # Copy blueprint directory
    rm -rf "$BLUEPRINT_DIR"
    cp -r blueprint "$BLUEPRINT_DIR"
    
    # Create target directory
    TARGET_DIR="$ENVS_DIR/$env/$service_name"
    mkdir -p "$TARGET_DIR"
}

replace_placeholders() {
    # Use portable sed commands that work on both Linux and macOS
    local files=$(find "$BLUEPRINT_DIR" -type f -name "*.yaml")
    
    for file in $files; do
        # Create backup and perform replacements
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS sed requires empty string after -i
            sed -i '' "s|<env>|$env|g" "$file"
            sed -i '' "s|<service-name>|$service_name|g" "$file"
            sed -i '' "s|<image-name>|$image_name|g" "$file"
            sed -i '' "s|<image-tag>|$image_tag|g" "$file"
        else
            # Linux sed
            sed -i "s|<env>|$env|g" "$file"
            sed -i "s|<service-name>|$service_name|g" "$file"
            sed -i "s|<image-name>|$image_name|g" "$file"
            sed -i "s|<image-tag>|$image_tag|g" "$file"
        fi
    done
}

build_components_list() {
    local components=()
    
    $use_postgres && components+=("postgres")
    $use_kafka && components+=("kafka")
    $use_mongodb && components+=("mongo")
    
    if [[ ${#components[@]} -eq 0 ]]; then
        COMPONENTS_LIST=""
        return
    fi
    
    COMPONENTS_LIST=""
    for component in "${components[@]}"; do
        if [[ -z "$COMPONENTS_LIST" ]]; then
            COMPONENTS_LIST="  - ../../base/components/$component
  - ../components/$component"
        else
            COMPONENTS_LIST="$COMPONENTS_LIST
  - ../../base/components/$component
  - ../components/$component"
        fi
    done
}

build_literals_list() {
    LITERALS_LIST="      - SERVICE_NAME=$service_name"
    
    if $use_postgres; then
        LITERALS_LIST="$LITERALS_LIST
      - POSTGRES_SCHEMA_NAME=${postgres_schema_name:-<postgres-schema-name>}"
    fi
    
    if $use_auth_url; then
        LITERALS_LIST="$LITERALS_LIST
      - AUTH_URL=${auth_url:-<auth-url>}"
    fi
}

process_kustomization_file() {
    local kustomization_file="$BLUEPRINT_DIR/kustomization.yaml"
    
    if [[ ! -f "$kustomization_file" ]]; then
        echo "Warning: kustomization.yaml not found"
        return
    fi
    
    # Create a temporary file for processing
    local temp_file=$(mktemp)
    
    # Process the file line by line
    while IFS= read -r line; do
        case "$line" in
            *"<components-list>"*)
                if [[ -n "$COMPONENTS_LIST" ]]; then
                    echo "$COMPONENTS_LIST"
                fi
                ;;
            *"<literals-list>"*)
                if [[ -n "$LITERALS_LIST" ]]; then
                    echo "$LITERALS_LIST"
                fi
                ;;
            *)
                echo "$line"
                ;;
        esac
    done < "$kustomization_file" > "$temp_file"
    
    # Move the processed file back
    mv "$temp_file" "$kustomization_file"
}

update_kustomization_image() {
    # Change to the target directory to use kustomize edit
    cd "$BLUEPRINT_DIR"
    
    # Check if kustomize is available
    if command -v kustomize &> /dev/null; then
        # Use kustomize edit to set the image
        kustomize edit set image "app-image=$image_name:$image_tag"
    else
        echo "Warning: kustomize command not found. Image settings were applied via sed."
    fi
    
    cd - > /dev/null
}

finalize_setup() {
    # Move files to target directory
    mv "$BLUEPRINT_DIR"/* "$TARGET_DIR/"
    rmdir "$BLUEPRINT_DIR"
}

display_summary() {
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
    echo
    echo "Hinweise:"
    echo "  - Die Konfiguration wurde in '$TARGET_DIR/kustomization.yaml' erstellt"
    echo "  - Überprüfen Sie die generierten Dateien vor dem Deployment"
    if ! command -v kustomize &> /dev/null; then
        echo "  - Installieren Sie 'kustomize' für erweiterte Funktionen"
    fi
}

# Main Execution
main() {
    echo "=== Service Initialization Script ==="
    echo
    
    # Gather all inputs
    select_environment
    gather_requirements
    gather_service_details
    
    # Prepare and process blueprint
    prepare_blueprint
    replace_placeholders
    
    # Build configuration
    build_components_list
    build_literals_list
    
    # Process kustomization file
    process_kustomization_file
    update_kustomization_image
    
    # Finalize
    finalize_setup
    display_summary
}

# Run main function
main