#!/bin/bash
set -e

OUTPUT_DIR="output"

# ================ Helpers Functions ================
prompt_yes_no() {
    local prompt="$1"
    local response
    while true; do
        read -r -p "$prompt (ja/nein - j/n): " response
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
    read -r -p "$prompt" value
    if [[ -z "$value" && -n "$default" ]]; then
        value="$default"
    fi
    eval "$var_name='$value'"
}

select_environment() {
    echo
    echo "In welcher Umgebung soll der Service bereitgestellt werden?"
    select picked_env in "dev" "tst" "abn" "prod"; do
        if [[ -n "$picked_env" ]]; then
            env="$picked_env"
            echo "Umgebung gewählt: '$env'"
            break
        else
            echo "Ungültige Auswahl."
        fi
    done
}

# Draws a box around the given lines, auto-fitting to the longest line. -> just for beauty
print_box() {
    local lines=("$@")
    local max_len=0
    for line in "${lines[@]}"; do
        local len=${#line}
        (( len > max_len )) && max_len=$len
    done
    local inner=$((max_len + 2))
    local top bot empty
    top="  ┌$(printf '─%.0s' $(seq 1 $inner))┐"
    bot="  └$(printf '─%.0s' $(seq 1 $inner))┘"
    empty="  │$(printf ' %.0s' $(seq 1 $inner))│"
    echo "$top"
    echo "$empty"
    for line in "${lines[@]}"; do
        local pad
        pad=$(( inner - ${#line} - 1 ))
        printf "  │ %s%${pad}s│\n" "$line" ""
    done
    echo "$empty"
    echo "$bot"
}

# ================ End Helpers ================

# Parse git link
#
#   https://git.system.local/scm/elpa/hint.git//copsi/tst?ref=8518f2abc1234
#   → service_name = hint
#   → env          = tst
#   → short_commit = 8518f2a   (always exactly 7 chars)
parse_git_link() {
    local link="$1"

    # Example: https://git.system.local/scm/elpa/hint.git//copsi/tst?ref=abc → hint
    local _strip_git="${link%.git*}"
    service_name="${_strip_git##*/}"

    # Example: ...//copsi/tst?ref=abc → tst
    local parsed_env _strip_copsi
    _strip_copsi="${link#*//copsi/}"
    parsed_env="${_strip_copsi%%\?*}"

    # Trimmed to 7 chars below (shortCommitId convention from si_docker.groovy)
    local raw_ref
    raw_ref="${link##*ref=}"
    short_commit="${raw_ref:0:7}"

    if [[ -z "$service_name" || -z "$raw_ref" ]]; then
        echo "  ❌  Ungültiger Git-Link. Erwartet:"
        echo "      https://git.system.local/scm/elpa/<service>.git//copsi/<env>?ref=<hash>"
        exit 1
    fi

    if [[ -n "$parsed_env" ]]; then
        env="$parsed_env"
        echo "  🔍  Erkannt: service='${service_name}'  env='${env}'  ref='${short_commit}'"
    else
        echo "  ⚠️   Env konnte nicht aus dem Git-Link gelesen werden."
        select_environment
    fi
}

# Derive image name and registry from env (base on image build in si_docker.groovy)
derive_image_name() {
    case "$env" in
        tst)
            REGISTRY="dev.docker.system.local"
            NAMESPACE="elpa-${service_name}-tst"
            ;;
        abn|prod)
            REGISTRY="prod.docker.system.local"
            NAMESPACE="elpa-${service_name}"
            ;;
        dev)
            #TODO: remove later, because we will use tst instead
            REGISTRY="dev.docker.system.local"
            NAMESPACE="elpa-${service_name}-dev"
            ;;
        *)
            echo "  ⚠️   Unbekannte Umgebung '$env' – Registry-Konvention unbekannt."
            prompt_value "  Registry (z.B. dev.docker.system.local): " REGISTRY
            prompt_value "  Namespace im Registry: " NAMESPACE
            ;;
    esac

    image_name="${REGISTRY}/${NAMESPACE}/${service_name}"
    echo "  📦  Image-Name: ${image_name}"
}

# Force the user to manually enter image_name and image_tag, when smart-finder do not work
force_manual_image_input() {
    echo
    print_box \
        "❌  Image-Tag konnte nicht automatisch ermittelt werden." \
        "Bitte Image-Name und Tag manuell eingeben."
    echo "  Vorgeschlagener Image-Name: ${image_name}"
    prompt_value "  Image-Name bestätigen oder überschreiben: " image_name "$image_name"
    while true; do
        prompt_value "  Image-Tag (Format: <7-char-commit>.<build-nr>, z.B. ${short_commit}.1): " image_tag
        if [[ -n "$image_tag" ]]; then
            break
        fi
        echo "  ⚠️  Image-Tag darf nicht leer sein."
    done
    echo "  ✅  Image-Tag gesetzt: ${image_tag}"
}

# Fetch the Docker Registry tag list for the current image.
fetch_registry_tags() {
    local tags_url="https://${REGISTRY}/v2/${NAMESPACE}/${service_name}/tags/list"
    echo "  🔎  Prüfe Registry: ${tags_url}"

    local result
    result=$(curl -s --max-time 5 --insecure -u "anonymous:anonymous" \
        "$tags_url" 2>/dev/null || true)

    # If anonymous access was denied, prompt once for credentials
    if echo "$result" | grep -q '"errors"'; then
        echo "  ⚠️   Anonymer Zugriff verweigert – Zugangsdaten eingeben (leer → überspringen):"
        local reg_user reg_pass
        read -r -p "      Benutzername: " reg_user
        if [[ -n "$reg_user" ]]; then
            read -rsp "      Passwort: " reg_pass
            echo
            result=$(curl -s --max-time 5 --insecure -u "${reg_user}:${reg_pass}" \
                "$tags_url" 2>/dev/null || true)
        else
            echo "  ⏭️   Registry-Check übersprungen."
            result=""
        fi
    fi

    # Return result via global (avoids subshell losing the value)
    REGISTRY_RESPONSE="$result"
}

resolve_image_tag() {
    local matched_tag
    local response

    if [[ "$env" == "tst" || "$env" == "abn" || "$env" == "prod" || "$env" == "dev" ]]; then
        fetch_registry_tags
        response="$REGISTRY_RESPONSE"
    else
        echo "  ⏭️   Registry-Check für env='${env}' nicht implementiert."
        response=""
    fi

    if [[ -z "$response" ]]; then
        echo "  ⚠️   Registry nicht erreichbar oder übersprungen."
        force_manual_image_input
        return
    fi

    # Check for registry error response, e.g. {"errors":[{"code":"UNAUTHORIZED","message":"..."}]}
    if echo "$response" | grep -q '"errors"'; then
        local err_msg
        # shellcheck disable=SC2001  # sed capture group needed; no bash-native equivalent
        err_msg=$(echo "$response" | sed -n 's|.*"message":"\([^"]*\)".*|\1|p' | head -1)
        echo "  ❌  Registry-Fehler: ${err_msg:-unbekannt}"
        force_manual_image_input
        return
    fi

    # Parse tags array: {"name":"...","tags":["8518f2a.1","8518f2a.2",...]}
    # 1. grep -oE '"[^"]+"' → extract every quoted string from JSON
    # 2. tr -d '"'           → strip the surrounding quotes
    # 3. grep "^<commit>."   → keep only tags belonging to this commit
    # 4. sort -t. -k2 -rn   → sort by build number (field 2) descending numerically
    # 5. head -1             → take the highest build number
    matched_tag=$(echo "$response" \
        | grep -oE '"[^"]+"' \
        | tr -d '"' \
        | grep "^${short_commit}\." \
        | sort -t. -k2 -rn \
        | head -1 || true)

    if [[ -n "$matched_tag" ]]; then
        image_tag="$matched_tag"
        echo "  ✅  Image-Tag gefunden: ${image_tag}"
    else
        echo "  ⚠️   Kein Tag für '${short_commit}.*' in der Registry gefunden."
        force_manual_image_input
    fi
}

# Gather input
gather_input() {
    # FIX: copsi_git_link must be global (no 'local') so it is accessible in
    # build_components_block() and main(), which are called from main() not from here.
    # bash 'local' scope only covers the declaring function and its callees.
    # 1. Git link first → drives service_name, env, short_commit
    echo
    echo "--- Copsi Git-Link ---"
    echo "Beispiel: https://git.system.local/scm/elpa/<service>.git//copsi/<env>?ref=<commit-hash>"
    echo
    prompt_value "Git-Link zum Copsi-Component: " copsi_git_link

    parse_git_link "$copsi_git_link"

    # 2. Derive image name from si_docker conventions
    echo
    derive_image_name

    # 3. Resolve image tag via registry (env-specific logic)
    echo
    resolve_image_tag

    # 4. Infra components
    echo
    echo "--- Infrastruktur-Komponenten (bleiben im Deploy-Repo) ---"
    prompt_yes_no "Wird PostgreSQL verwendet?" && use_postgres=true || use_postgres=false
    prompt_yes_no "Wird Kafka verwendet?"      && use_kafka=true    || use_kafka=false
}

# Build components block
build_components_block() {
    local component_lines="  - ${copsi_git_link}"

    if $use_postgres; then
        component_lines="${component_lines}
  - ../../base/components/postgres
  - ../components/postgres"
    fi

    if $use_kafka; then
        component_lines="${component_lines}
  - ../../base/components/kafka
  - ../components/kafka"
    fi

    component_lines="${component_lines}
  - ../components/environments
  - ../../base/components/environments"

    COMPONENTS_BLOCK="components:
${component_lines}"
}

# Write kustomization.yaml to output/
write_kustomization() {
    local target_dir="${OUTPUT_DIR}/${service_name}"
    mkdir -p "$target_dir"
    TARGET_FILE="${target_dir}/kustomization.yaml"

    cat > "$TARGET_FILE" << YAML
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

nameSuffix: "-${service_name}"

resources:
  - ../../base

${COMPONENTS_BLOCK}

images:
  - name: app-image
    newName: ${image_name}
    newTag: ${image_tag}
YAML
    echo
    echo "  📄  Kustomization geschrieben: ${TARGET_FILE}"
}

# Update only the copsi ref and image tag in an existing kustomization.yaml.
# All other content (custom patches, extra literals, etc.) is preserved.
update_existing_kustomization() {
    local target_file="$1"
    local new_ref="${copsi_git_link##*ref=}"

    # Update copsi git-link: replace the old ref= hash with the new one
    # Update image tag: replace the newTag line
    if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' "s|//copsi/${env}?ref=[a-f0-9]*|//copsi/${env}?ref=${new_ref}|g" "$target_file"
        sed -i '' "s|newTag:.*|newTag: ${image_tag}|" "$target_file"
    else
        sed -i "s|//copsi/${env}?ref=[a-f0-9]*|//copsi/${env}?ref=${new_ref}|g" "$target_file"
        sed -i "s|newTag:.*|newTag: ${image_tag}|" "$target_file"
    fi

    echo "  🔄  Aktualisiert: ref   → ${new_ref}"
    echo "  🔄  Aktualisiert: newTag → ${image_tag}"
}

# Copy generated file into deploy repo and register it.
# If kustomization.yaml already exists, only ref and image tag are updated.
deploy_to_repo() {
    local deploy_repo="$1"
    local overlay_dir="${deploy_repo}/envs/${env}/${service_name}"
    local overlay_file="${overlay_dir}/kustomization.yaml"
    local env_kustomization="${deploy_repo}/envs/${env}/kustomization.yaml"

    if [[ ! -f "$env_kustomization" ]]; then
        echo "  ❌  '${env_kustomization}' nicht gefunden."
        echo "      Bitte Pfad prüfen und manuell ausführen:"
        echo
        echo "      mkdir -p ${overlay_dir}"
        echo "      cp ${TARGET_FILE} ${overlay_dir}/kustomization.yaml"
        echo
        echo "      Und in ${env_kustomization} unter resources eintragen:"
        echo "        - ${service_name}"
        return 1
    fi

    mkdir -p "$overlay_dir"

    if [[ -f "$overlay_file" ]]; then
        # Service already deployed before – preserve manual changes, only update what changed
        echo "  ♻️   Kustomization existiert bereits – aktualisiere ref und image tag."
        update_existing_kustomization "$overlay_file"
    else
        # First deployment – write the full generated file
        cp "$TARGET_FILE" "$overlay_file"
        echo "  ✅  Kopiert nach: ${overlay_file}"
    fi

    local entry="  - ${service_name}"
    if grep -q "^${entry}$" "$env_kustomization" 2>/dev/null; then
        echo "  ⏭️   '${service_name}' bereits in ${env_kustomization} registriert."
    else
        awk -v res="$entry" '/resources:/{print; print res; next} {print}' \
            "$env_kustomization" > "${env_kustomization}.tmp" && \
        mv "${env_kustomization}.tmp" "$env_kustomization"
        echo "  ✅  '${service_name}' in ${env_kustomization} registriert."
    fi
}

main() {
    echo "=== init-service.sh ==="

    gather_input
    build_components_block
    write_kustomization

    # copsi-init is always cloned inside elpa-elpa4, so the deploy repo is the parent directory
    local deploy_repo_path
    deploy_repo_path="$(cd .. && pwd)"
    echo "  📁  Deploy-Repo: ${deploy_repo_path}"

    echo
    deploy_to_repo "$deploy_repo_path"

    echo
    echo "======================================================================"
    echo "  ✅ ✅ ✅  Fertig!  ✅ ✅ ✅"
    echo "======================================================================"
    echo
    echo "  Service  : ${service_name}"
    echo "  Umgebung : ${env}"
    echo "  Registry : ${REGISTRY}"
    echo "  Image    : ${image_name}:${image_tag}"
    echo "  Copsi    : ${copsi_git_link}"
    echo

    # Warn about manual steps that must be done before ArgoCD deploys
    local kafka_warning=()
    local deploy_warning=()

    if $use_kafka; then
        kafka_warning=(
            "⚠️  KAFKA: Alle Topics von '${service_name}' müssen in"
            "   kafka/values.yaml registriert werden, bevor der Service"
            "   deployed wird – sonst schlägt die Kafka-Verbindung fehl."
            ""
            "   Datei: ${deploy_repo_path}/kafka/values.yaml"
        )
    fi

    deploy_warning=(
        "⚠️  DEPLOY: Service '${service_name}' muss in der Umgebungs-"
        "   Kustomization registriert werden (falls nicht automatisch):"
        ""
        "   Datei : ${deploy_repo_path}/envs/${env}/kustomization.yaml"
        "   Eintrag unter resources:"
        "     - ${service_name}"
    )

    if $use_kafka; then
        print_box "${kafka_warning[@]}"
        echo
    fi
    print_box "${deploy_warning[@]}"
    echo
}

main