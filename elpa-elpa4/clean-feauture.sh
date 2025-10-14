#!/bin/bash
set -e # Exit on error

JIRA_SUFFIX="ELPA4-123"
SERVICE_NAME="hint-service" # This assumes the base service name for the feature

DEV_PATH="./envs/dev"
FEATURE_NAME="$SERVICE_NAME-$JIRA_SUFFIX"
FEATURE_PATH="$DEV_PATH/$FEATURE_NAME"

# Function to remove the feature directory
remove_feature_directory() {
    echo "Attempting to remove feature directory: $FEATURE_PATH"
    if [ -d "$FEATURE_PATH" ]; then
        rm -rf "$FEATURE_PATH"
        echo "Successfully removed directory: $FEATURE_PATH"
    else
        echo "Directory not found, skipping removal: $FEATURE_PATH"
    fi
}

# Function to remove the feature entry from envs/dev/kustomization.yaml
remove_feature_from_resources() {
    local kustomization_file="$DEV_PATH/kustomization.yaml"
    # The exact line format to remove, including indentation
    local feature_resource_line="  - $FEATURE_NAME"

    echo "Attempting to remove '$feature_resource_line' from $kustomization_file resources."

    # Check if the feature resource already exists in the file
    if ! grep -q "^${feature_resource_line}\$" "$kustomization_file"; then
        echo "'$FEATURE_NAME' does not exist in $kustomization_file resources. Skipping removal."
        return 0
    fi

    local escaped_feature_name="${FEATURE_NAME//\//\\/}"
    local sed_pattern="^  - ${escaped_feature_name}\$"

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # For macOS (BSD sed)
        sed -i '' "/$sed_pattern/d" "$kustomization_file"
    else
        # For Linux (GNU sed)
        sed -i "/$sed_pattern/d" "$kustomization_file"
    fi
    echo "Successfully removed '$FEATURE_NAME' from $kustomization_file resources."
}

# Main Execution for clean-feature
main_clean() {
    remove_feature_from_resources
    remove_feature_directory
}

# Run main clean function
main_clean