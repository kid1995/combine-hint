#!/bin/bash
set -e  # Exit on error

#DEBUG
# SERVICE_NAME="hint"
# SERVICE_SUFFIX="-${SERVICE_NAME}"
# JIRA_TICKET="ELPA4-123"
# IMAGE_NAME="docker.system.local/elpa-hint-ELPA4-123-tst/hint:abcdef.12"

SERVICE_NAME=$1
SERVICE_SUFFIX=$2
JIRA_TICKET=$3
IMAGE_NAME=$4

DEV_PATH="./envs/dev"
# Sanitize JIRA ticket (trim to 32 chars, allow A-Z0-9- only)
JIRA_TICKET=$(echo "$JIRA_TICKET" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9-')
JIRA_TICKET=${JIRA_TICKET:0:32}

FEATURE_NAME="$SERVICE_NAME-$JIRA_TICKET"
SERVICE_PATH="$DEV_PATH/$SERVICE_NAME"
FEATURE_PATH="$DEV_PATH/$FEATURE_NAME"

clone_original_service(){
    echo "clone from original service"
    cp -r "$SERVICE_PATH" "$FEATURE_PATH"
}

replace_suffix_name() {    
    local file="$FEATURE_PATH/kustomization.yaml"
    local replace_string="${SERVICE_SUFFIX}-${JIRA_TICKET}"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then   #(macos use BSD, linux use GNU)
        sed -i '' "s|${SERVICE_SUFFIX}|$replace_string|g" "$file"
    else
        sed -i "s|${SERVICE_SUFFIX}|$replace_string|g" "$file"
    fi
}

update_istio_hosts() {
    # Compute target Service DNS name used by Istio objects inside dev overlay
    local feature_suffix="${SERVICE_SUFFIX}-${JIRA_TICKET}"

    # Files that may contain Istio destination host definitions
    local files=(
        "$FEATURE_PATH/virtualservice-patch.yaml"
        "$FEATURE_PATH/metrics-virtualservice-patch.yaml"
        "$FEATURE_PATH/destination-rule.yaml"        
    )

    for f in "${files[@]}"; do
        [ -f "$f" ] || continue
        if [[ "$OSTYPE" == darwin* ]]; then   #(macos use BSD, linux use GNU)
            # BSD sed requires an empty string for the -i argument when not creating a backup.
            # Use a consistent delimiter (e.g., '/') and quote variables for robustness.
            sed -i '' "s|${SERVICE_SUFFIX}|${feature_suffix}|g" "$f"
        else
            # GNU sed. Use a consistent delimiter (e.g., '/') and quote variables.
            sed -i "s|${SERVICE_SUFFIX}|${feature_suffix}|g" "$f"
        fi
    done
}

update_kustomization_image() {
    # Change to the target directory to use kustomize edit
    cd "$FEATURE_PATH" || exit 1
    # Check if kustomize is available
    if command -v kustomize &> /dev/null; then
        kustomize edit set image "app-image=$IMAGE_NAME"
    else
        echo "Warning: kustomize command not found. Image settings were applied via sed."        
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|app-image:.*|app-image=$IMAGE_NAME|" "$FEATURE_PATH/kustomization.yaml"
        else
            sed -i "s|app-image:.*|app-image=$IMAGE_NAME|" "$FEATURE_PATH/kustomization.yaml"
        fi
    fi

    cd - > /dev/null
}

add_feature_into_resources() {
    local kustomization_file="$DEV_PATH/kustomization.yaml"
    local feature_resource_name="$FEATURE_NAME" # This is the folder name that kustomize will look for

    if grep -q "^  - $feature_resource_name\$" "$kustomization_file"; then
        echo "'$feature_resource_name' already exists in $kustomization_file."
        return 0
    fi

    echo "Adding '$feature_resource_name' to $kustomization_file resources."

    awk -v resource_to_add="  - $feature_resource_name" '
        /resources:/ {
            print; # Print the "resources:" line itself
            print resource_to_add; # Then print the new resource (on its own line)
            next; # Skip the rest of the script for this line and go to the next input line
        }
        { print } # Print all other lines as they are
    ' "$kustomization_file" > "${kustomization_file}.tmp" && \
    mv "${kustomization_file}.tmp" "$kustomization_file"
}


# Main Execution
main(){
    #reset before execute
    rm -rf "$FEATURE_PATH"
    # Prepare features deployment folder
    clone_original_service
    #
    replace_suffix_name
    #
    update_kustomization_image
    #
    update_istio_hosts
    #
    add_feature_into_resources

}

# Run main function
main