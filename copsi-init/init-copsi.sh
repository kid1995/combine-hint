#!/bin/bash
set -e  # Exit on error

BLUEPRINT_DIR="blueprint"
COPSI_DIR="copsi"
ENVS=("dev" "abn" "prod")

# Helpers

prompt_yes_no() {
    local prompt="$1"
    local response
    while true; do
        read -p "$prompt (ja/nein - j/n): " response
        if [[ -z "$response" ]]; then
            echo "Keine Eingabe. 'nein' wurde angenommen."
            return 1
        fi
        response=$(echo "$response" | tr '[:upper:]' '[:lower:]')
        if [[ "$response" =~ ^(ja|j|true|y)$ ]];  then return 0
        elif [[ "$response" =~ ^(nein|ne|n|false|jein|jain)$ ]]; then return 1
        else echo "Ungültige Eingabe. Bitte geben Sie 'ja' oder 'nein' ein."
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

# Input

gather_input() {
    echo
    prompt_value "Wie lautet der Service-Name? (z.B. 'hint'): " service_name

    echo
    prompt_yes_no "Wird PostgreSQL verwendet?" && use_postgres=true || use_postgres=false
    prompt_yes_no "Wird Kafka verwendet?"      && use_kafka=true    || use_kafka=false
    prompt_yes_no "Wird OAuth2 (AUTH_URL) benötigt?" && use_auth_url=true || use_auth_url=false

    if $use_postgres; then
        prompt_value "PostgreSQL Schema-Name? (z.B. '${service_name}'): " postgres_schema_name "$service_name"
    fi
}

# Build the configMapGenerator literals block for one environment.
# Result is stored in $LITERALS (empty string if nothing to add).

build_literals_for_env() {
    local env="$1"

    local auth_url=""
    if $use_auth_url; then
        case "$env" in
            dev)  auth_url="https://employee.login.int.signal-iduna.org/" ;;
            abn)  auth_url="https://employee.login.abn.signal-iduna.org/" ;;
            prod) auth_url="https://employee.login.signal-iduna.org/" ;;
        esac
    fi

    LITERALS="      - SERVICE_NAME=${service_name}"

    if $use_postgres; then
        LITERALS="${LITERALS}
      - POSTGRES_SCHEMA_NAME=${postgres_schema_name}"
    fi

    if $use_auth_url; then
        LITERALS="${LITERALS}
      - AUTH_URL=${auth_url}"
    fi
}

# Inject literals into kustomization.yaml.
# Drops the entire configMapGenerator block when LITERALS is empty.
# Uses a pending-header pattern identical to init-service.sh.

process_kustomization_file() {
    local kustomization_file="$1"
    local temp_file
    temp_file=$(mktemp)

    local pending_header=""   # holds buffered lines above <literals-list>

    while IFS= read -r line; do
        if [[ "$line" == *"<literals-list>"* ]]; then
            if [[ -n "$LITERALS" ]]; then
                echo "$pending_header"
                echo "$LITERALS"
            fi
            # Either way: discard placeholder and buffered header
            pending_header=""
            continue
        fi

        # Detect start of the configMapGenerator block
        if [[ "$line" =~ ^configMapGenerator:[[:space:]]*$ ]]; then
            pending_header="$line"
            continue
        fi

        # While we are inside a pending configMapGenerator block,
        # keep buffering (name:, behavior:, literals:)
        if [[ -n "$pending_header" ]]; then
            pending_header="${pending_header}
${line}"
            continue
        fi

        echo "$line"
    done < "$kustomization_file" > "$temp_file"

    mv "$temp_file" "$kustomization_file"
}

# Replace <env> and <service-name> in all yaml files under a directory

replace_placeholders() {
    local target_dir="$1"
    local env="$2"
    local files
    files=$(find "$target_dir" -type f -name "*.yaml")

    for file in $files; do
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|<env>|${env}|g"                   "$file"
            sed -i '' "s|<service-name>|${service_name}|g" "$file"
        else
            sed -i "s|<env>|${env}|g"                   "$file"
            sed -i "s|<service-name>|${service_name}|g" "$file"
        fi
    done
}


# Create one copsi/<env> folder from the blueprint

create_env_folder() {
    local env="$1"
    local target_dir="${COPSI_DIR}/${env}"

    if [[ -d "$target_dir" ]]; then
        echo "  ⏭️  ${target_dir}/ already exists – skipping"
        return 0
    fi

    echo "  Processing ${target_dir}/ ..."

    mkdir -p "${COPSI_DIR}"
    cp -r "$BLUEPRINT_DIR" "$target_dir"

    # 1. Replace <env> and <service-name> in all yaml files
    replace_placeholders "$target_dir" "$env"

    # 2. Build env-specific literals and inject them into kustomization.yaml
    build_literals_for_env "$env"
    process_kustomization_file "${target_dir}/kustomization.yaml"

    echo "  ✅ ${target_dir}/"
}

# Find the first copsi/<env> folder that already exists and read its config.
# Extracts: service_name, use_postgres, postgres_schema_name, use_auth_url
# Returns 0 if config was read successfully, 1 if nothing exists yet.

read_config_from_existing() {
    local existing_kustomization=""

    for env in "${ENVS[@]}"; do
        if [[ -f "${COPSI_DIR}/${env}/kustomization.yaml" ]]; then
            existing_kustomization="${COPSI_DIR}/${env}/kustomization.yaml"
            echo "  📖 Lese Konfiguration aus vorhandener Datei: ${existing_kustomization}"
            break
        fi
    done

    [[ -z "$existing_kustomization" ]] && return 1

    # SERVICE_NAME
    service_name=$(grep -oP '(?<=SERVICE_NAME=)\S+' "$existing_kustomization" || true)

    # POSTGRES_SCHEMA_NAME
    postgres_schema_name=$(grep -oP '(?<=POSTGRES_SCHEMA_NAME=)\S+' "$existing_kustomization" || true)
    if [[ -n "$postgres_schema_name" ]]; then
        use_postgres=true
    else
        use_postgres=false
    fi

    # AUTH_URL (presence is enough – the URL is rebuilt per env)
    local auth_url_found
    auth_url_found=$(grep -c 'AUTH_URL=' "$existing_kustomization" || true)
    if [[ "$auth_url_found" -gt 0 ]]; then
        use_auth_url=true
    else
        use_auth_url=false
    fi

    # kafka: check if kafka-related env var appears in deployment-patch
    use_kafka=false
    if [[ -f "${COPSI_DIR}/$(ls ${COPSI_DIR} | head -1)/deployment-patch.yaml" ]]; then
        if grep -q 'KAFKA' "${COPSI_DIR}/$(ls ${COPSI_DIR} | head -1)/deployment-patch.yaml" 2>/dev/null; then
            use_kafka=true
        fi
    fi

    echo "  ✅ Erkannte Konfiguration:"
    echo "     SERVICE_NAME        = ${service_name}"
    echo "     use_postgres        = ${use_postgres}"
    [[ -n "$postgres_schema_name" ]] && echo "     POSTGRES_SCHEMA_NAME = ${postgres_schema_name}"
    echo "     use_auth_url        = ${use_auth_url}"
    echo "     use_kafka           = ${use_kafka}"

    return 0
}

main() {
    echo "=== init-copsi.sh – Kustomize Component Generator ==="
    echo

    if [[ ! -d "$BLUEPRINT_DIR" ]]; then
        echo "❌  Blueprint-Verzeichnis '$BLUEPRINT_DIR' nicht gefunden."
        echo "    Stelle sicher, dass du das Script aus dem Root des Service-Repositories ausführst."
        exit 1
    fi

    # Count how many env folders are missing
    missing_envs=()
    for env in "${ENVS[@]}"; do
        [[ ! -d "${COPSI_DIR}/${env}" ]] && missing_envs+=("$env")
    done

    if [[ ${#missing_envs[@]} -eq 0 ]]; then
        echo "  ✅ Alle copsi-Verzeichnisse (${ENVS[*]}) sind bereits vorhanden. Nichts zu tun."
        exit 0
    fi

    echo "  Fehlende Verzeichnisse: ${missing_envs[*]}"
    echo

    # If some envs already exist, read config from them automatically.
    # If none exist yet, ask the user.
    if read_config_from_existing; then
        echo
        echo "  ℹ️  Fehlende Verzeichnisse werden automatisch generiert..."
    else
        echo "  Keine vorhandenen copsi-Verzeichnisse gefunden – Konfiguration abfragen..."
        gather_input
    fi

    echo
    echo "Erstelle fehlende copsi-Verzeichnisse..."
    echo

    for env in "${missing_envs[@]}"; do
        create_env_folder "$env"
    done

    echo
    echo "======================================================================"
    echo "  ✅ ✅ ✅  Copsi erfolgreich erstellt!  ✅ ✅ ✅"
    echo "======================================================================"
    echo
    echo "Generierte Dateien:"
    find "${COPSI_DIR}" -name "*.yaml" | sort | sed 's/^/  /'
    echo
    echo "Nächste Schritte:"
    echo "  1. git add copsi/ && git commit -m 'feat: add copsi kustomize components'"
    echo "  2. git push"
    echo "  3. git rev-parse HEAD   ← Commit-Hash für den Git-Link im Deploy-Repo"
    echo
    echo "Git-Link Vorlage für deploy-repo (init-service.sh):"
    echo "  https://git.system.local/scm/elpa/${service_name}.git//copsi/<env>?ref=<commit-hash>"
    echo
}

main
