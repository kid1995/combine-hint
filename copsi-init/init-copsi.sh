#!/bin/bash
set -e

BLUEPRINT_DIR="blueprint"
COPSI_DIR="copsi"
ENVS=("tst" "abn" "prod")

# ================ Helpers Functions ================
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
        if   [[ "$response" =~ ^(ja|j|true|y)$ ]];               then return 0
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

# Draws a box around the given lines, auto-fitting to the longest line.
print_box() {
    local lines=("$@")
    local max_len=0
    for line in "${lines[@]}"; do
        local len=${#line}
        (( len > max_len )) && max_len=$len
    done
    local inner=$((max_len + 2))
    local top="  ┌$(printf '─%.0s' $(seq 1 $inner))┐"
    local bot="  └$(printf '─%.0s' $(seq 1 $inner))┘"
    local empty="  │$(printf ' %.0s' $(seq 1 $inner))│"
    echo "$top"
    echo "$empty"
    for line in "${lines[@]}"; do
        local pad=$(( inner - ${#line} - 1 ))
        printf "  │ %s%${pad}s│\n" "$line" ""
    done
    echo "$empty"
    echo "$bot"
}

# Indents every line of stdin by N spaces.
# Usage: echo "$LITERALS" | indent_lines 6
indent_lines() {
    local n="$1"
    local pad
    pad=$(printf '%*s' "$n" '')
    sed "s/^/${pad}/"
}

# ================ End Helpers ================

# Pre-flight: ensure working tree is clean before we start
check_git_clean() {
    echo "--- Git-Status Prüfung ---"

    # FIX: init-copsi.sh runs from inside copsi-init/ (cloned into the service repo).
    # All git operations must target the parent (service repo), not copsi-init/ itself,
    # because copsi-init/ has no .git after 'rm -rf .git' in the clone workflow.
    local repo_dir
    repo_dir="$(cd .. && pwd)"

    # Not inside a git repo at all?
    if ! git -C "$repo_dir" rev-parse --is-inside-work-tree > /dev/null 2>&1; then
        echo "  ❌  Dieses Verzeichnis ist kein Git-Repository."
        echo "      Bitte in das Service-Repository wechseln und erneut starten."
        exit 1
    fi

    echo "  📂  Repo: ${repo_dir}"

    # Any uncommitted changes (staged or unstaged)?
    if ! git -C "$repo_dir" diff --quiet || ! git -C "$repo_dir" diff --cached --quiet; then
        echo
        print_box \
            "⚠️   Es gibt noch nicht committete Änderungen!" \
            "" \
            "Das Script erzeugt am Ende einen Git-Link mit dem aktuellen" \
            "Commit-Hash. Wenn ungespeicherte Änderungen existieren," \
            "würde der Link auf einen veralteten Stand zeigen." \
            "" \
            "Bitte erst committen, dann das Script neu starten:" \
            "  git add ." \
            "  git commit -m 'deine Nachricht'"
        echo
        git -C "$repo_dir" status --short
        echo
        exit 1
    fi

    # Untracked files that the user might have forgotten to add?
    local untracked
    untracked=$(git -C "$repo_dir" ls-files --others --exclude-standard)
    if [[ -n "$untracked" ]]; then
        echo
        echo "  ⚠️   Es gibt ungetrackte Dateien (nicht in .gitignore):"
        echo "$untracked" | sed 's/^/      /'
        echo
        if ! prompt_yes_no "  Trotzdem fortfahren?"; then
            echo "  Abgebrochen."
            exit 1
        fi
    fi

    echo "  ✅  Working tree ist sauber – weiter geht's."
    echo
}

# Backup
# copsi → copsi-old, if that exists → copsi-old-1, copsi-old-2, ...
backup_existing_copsi() {
    [[ ! -d "$COPSI_DIR" ]] && return 0

    local backup="copsi-old"
    if [[ ! -d "$backup" ]]; then
        mv "$COPSI_DIR" "$backup"
    else
        local i=1
        while [[ -d "copsi-old-${i}" ]]; do
            (( i++ ))
        done
        mv "$COPSI_DIR" "copsi-old-${i}"
        backup="copsi-old-${i}"
    fi

    echo "  📦  Vorhandenes copsi/ gesichert als: ${backup}/"
}

# Gather input
gather_input() {
    echo
    prompt_value "Wie lautet der Service-Name? (z.B. 'hint'): " service_name

    echo
    prompt_yes_no "Wird PostgreSQL verwendet?"        && use_postgres=true  || use_postgres=false
    prompt_yes_no "Wird Kafka verwendet?"             && use_kafka=true     || use_kafka=false
    prompt_yes_no "Wird OAuth2 (AUTH_URL) benötigt?" && use_auth_url=true  || use_auth_url=false

    if $use_postgres; then
        prompt_value "PostgreSQL Schema-Name für tst (z.B. '${service_name}'): "             postgres_schema_name_tst "$service_name"
        prompt_value "PostgreSQL Schema-Name für abn/prod (z.B. '${service_name}'): "             postgres_schema_name_prod "$service_name"
    fi
}

# Build configMapGenerator literals per environment.
# Items are stored WITHOUT indentation; the indent is applied once in
# process_kustomization_file() to avoid fragile space-counting here.
build_literals_for_env() {
    local env="$1"

    # Plain list entries – no leading spaces
    LITERALS="- SERVICE_NAME=${service_name}"

    if $use_postgres; then
        local postgres_schema_name
        case "$env" in
            tst)  postgres_schema_name="$postgres_schema_name_tst" ;;
            abn|prod) postgres_schema_name="$postgres_schema_name_prod" ;;
        esac
        LITERALS="${LITERALS}
- POSTGRES_SCHEMA_NAME=${postgres_schema_name}"
    fi

    if $use_auth_url; then
        local auth_url
        case "$env" in
            tst)  auth_url="https://employee.login.int.signal-iduna.org/" ;;
            abn)  auth_url="https://employee.login.abn.signal-iduna.org/" ;;
            prod) auth_url="https://employee.login.signal-iduna.org/"     ;;
        esac
        LITERALS="${LITERALS}
- AUTH_URL=${auth_url}"
    fi
}

# Inject literals into kustomization.yaml
# Drops the entire configMapGenerator block when LITERALS is empty
process_kustomization_file() {
    local kustomization_file="$1"
    local temp_file
    temp_file=$(mktemp)
    local pending_header=""
    while IFS= read -r line; do
        if [[ "$line" == *"<literals-list>"* ]]; then
            if [[ -n "$LITERALS" ]]; then
                echo "$pending_header"
                # 6 spaces = 3 levels of 2-space YAML indent (under "literals:")
                echo "$LITERALS" | indent_lines 6
            fi
            pending_header=""
            continue
        fi
        if [[ "$line" =~ ^configMapGenerator:[[:space:]]*$ ]]; then
            pending_header="$line"
            continue
        fi
        if [[ -n "$pending_header" ]]; then
            pending_header="${pending_header}
${line}"
            continue
        fi
        echo "$line"
    done < "$kustomization_file" > "$temp_file"

    mv "$temp_file" "$kustomization_file"
}

# Replace placeholders in all yaml files
replace_placeholders() {
    local target_dir="$1"
    local env="$2"

    while IFS= read -r -d '' file; do
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|<env>|${env}|g"                   "$file"
            sed -i '' "s|<service-name>|${service_name}|g" "$file"
        else
            sed -i "s|<env>|${env}|g"                   "$file"
            sed -i "s|<service-name>|${service_name}|g" "$file"
        fi
    done < <(find "$target_dir" -type f -name "*.yaml" -print0)
}

# Create one copsi/<env> from the blueprint
create_env_folder() {
    local env="$1"
    local target_dir="${COPSI_DIR}/${env}"

    cp -r "$BLUEPRINT_DIR" "$target_dir"
    replace_placeholders "$target_dir" "$env"
    build_literals_for_env "$env"
    process_kustomization_file "${target_dir}/kustomization.yaml"

    echo "  ✅  ${target_dir}/"
}

# Commit, push and generate git-links for all environments
commit_push_and_generate_links() {
    echo
    echo "--- Git Commit & Push ---"

    local commit_msg
    prompt_value "Commit-Nachricht [feat(${service_name}): add copsi config]: " \
        commit_msg "feat(${service_name}): add copsi config"

    # FIX: copsi/ was generated inside copsi-init/ (current working dir).
    # Move it to the service repo root (parent) before git add,
    # because copsi-init/ itself is gitignored in the service repo.
    if [[ -d "../copsi" ]]; then
        rm -rf "../copsi"
    fi
    mv "$COPSI_DIR" "../copsi"
    echo "  📦  copsi/ → ../copsi/ verschoben"

    # FIX: all git commands use -C .. to target the parent (service repo),
    # not the current copsi-init/ directory which has no .git.
    git -C .. add copsi/
    git -C .. commit -m "$commit_msg"
    git -C .. push -u origin HEAD

    # Read the exact hash after push – this is what the registry will build against
    local commit_hash
    commit_hash=$(git -C .. rev-parse HEAD)
    local short_hash="${commit_hash:0:7}"

    echo
    echo "======================================================================"
    echo "  ✅ ✅ ✅  Fertig!  ✅ ✅ ✅"
    echo "======================================================================"
    echo
    echo "  Commit : ${commit_hash}"
    echo
    echo "Git-Links (nach erfolgreichem Jenkins-Build verwendbar):"
    echo
    for env in "${ENVS[@]}"; do
        echo "  ${env}: https://git.system.local/scm/elpa/${service_name}.git//copsi/${env}?ref=${commit_hash}"
    done
    echo
    print_box \
        "⏳  WICHTIG: Git-Links erst nach erfolgreichem Build nutzbar!" \
        "" \
        "Der Push hat soeben einen Jenkins-Build ausgelöst." \
        "Das Docker-Image wird erst gebaut und in die Registry gepusht," \
        "wenn der Build erfolgreich durchgelaufen ist." \
        "" \
        "➡  Jenkins-Build abwarten, dann init-service.sh ausführen."
    echo
}

main() {
    echo "=== init-copsi.sh ==="
    echo

    check_git_clean

    backup_existing_copsi
    mkdir -p "$COPSI_DIR"

    gather_input

    echo
    echo "Erstelle copsi-Verzeichnisse..."
    for env in "${ENVS[@]}"; do
        create_env_folder "$env"
    done

    # Copy get-git-links.sh into copsi/ so it stays in the service repo
    # and can be called after every push to regenerate the git-links
    cp "$(dirname "$0")/get-git-links.sh" "${COPSI_DIR}/get-git-links.sh"
    chmod +x "${COPSI_DIR}/get-git-links.sh"
    echo "  ✅  copsi/get-git-links.sh hinzugefügt"

    commit_push_and_generate_links
}

main