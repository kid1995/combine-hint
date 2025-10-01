#!/bin/bash

DEV_PATH="envs/dev"
SERVICE_NAME="hint-service"
JIRA_SUFFIX="ELPA-1035-a-long-text-to-test-the-deploy-feature"

reduce_jira_suffix_length() {
    echo "$JIRA_SUFFIX" | cut -c 1-32
}

clone_original_service() {
    echo "Cloning original service..."
    cp -r "$DEV_PATH/$SERVICE_NAME" "$DEV_PATH/$SERVICE_NAME-$JIRA_SUFFIX"
    cd "$DEV_PATH/$SERVICE_NAME-$JIRA_SUFFIX"
    sed -i '' "/namePrefix: /c\namePrefix: \"$JIRA_SUFFIX-\"" kustomization.yaml
}

reduce_jira_suffix_length > output.txt
clone_original_service