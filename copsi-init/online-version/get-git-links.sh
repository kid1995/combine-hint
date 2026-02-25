#!/bin/bash
# Prints the copsi git-links for all environments based on the current commit.
# Run this from the service repo root after every push to get the links
# needed for init-service.sh in the deploy repo (elpa-elpa4).

svc=$(basename "$(git remote get-url origin)") && \
hash=$(git rev-parse HEAD) && \
echo && \
echo "Git-Links (nach erfolgreichem Jenkins-Build verwendbar):" && \
echo && \
for env in tst abn prod; do
    echo "  ${env}: https://git.system.local/scm/elpa/${svc}//copsi/${env}?ref=${hash}"
done && \
echo

