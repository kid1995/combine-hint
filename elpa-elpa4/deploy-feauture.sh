#!/bin/bash
set -e  # Exit on error

SERVICE_NAME="hint-service"
SERVICE_SUFFIX="-hint"
JIRA_SUFFIX="ELPA4-123"
FULL_IMAGE_NAME="docker.system.local/elpa-hint-ELPA4-123-tst/hint:abcdef.12"

DEV_PATH="./envs/dev"
FEATURE_NAME="$SERVICE_NAME-$JIRA_SUFFIX"
SERVICE_PATH="$DEV_PATH/$SERVICE_NAME"
FEATURE_PATH="$DEV_PATH/$FEATURE_NAME"

clone_original_service(){
    echo "clone from original service"
    cp -r "$SERVICE_PATH" "$FEATURE_PATH"
}

extract_image_tag() {
  local full_image_name="$1"
  if [[ -z "$full_image_name" ]]; then
    echo "Error: No full image name provided." >&2
    return 1
  fi
  
  # Extracts everything after the last colon
  echo "${full_image_name##*:}"
}

extract_image_name_without_tag() {
  local full_image_name="$1"
  echo "${full_image_name%:*}"
}

replace_suffix_name() {    
    local file="$FEATURE_PATH/kustomization.yaml"
    local replace_string="${SERVICE_SUFFIX}-${JIRA_SUFFIX}"
    
    if [[ "$OSTYPE" == "darwin"* ]]; then   #(macos use BSD, linux use GNU)
        sed -i '' "s|${SERVICE_SUFFIX}|$replace_string|g" "$file"
    else
        sed -i "s|${SERVICE_SUFFIX}|$replace_string|g" "$file"
    fi
}

update_kustomization_image() {
    # Change to the target directory to use kustomize edit
    cd "$FEATURE_PATH" || exit 1
    # Check if kustomize is available
    if command -v kustomize &> /dev/null; then
        kustomize edit set image "app-image=$FULL_IMAGE_NAME"
    else
        echo "Warning: kustomize command not found. Image settings were applied via sed."
        # Fallback to sed if kustomize is not found
        # (This sed command is a generic example, you might need to tailor it to your specific kustomization structure)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|app-image:.*|app-image=$FULL_IMAGE_NAME|" "$FEATURE_PATH/kustomization.yaml"
        else
            sed -i "s|app-image:.*|app-image=$FULL_IMAGE_NAME|" "$FEATURE_PATH/kustomization.yaml"
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
    add_feature_into_resources

}

# Run main function
main