import de.signaliduna.BitbucketRepo

@Field
String GIT_CLONE_DIR = "cd-repos"

@Field
String GIT_CREDENTIALS_ID = "jenkins_git_http"

boolean deployTstFeature()(BitbucketRepo repo, String targetBranch, String serviceName, String serviceSuffix, String jiraTicket, String imageName) {
    sh "mkdir -p ${GIT_CLONE_DIR}"
    dir(GIT_CLONE_DIR) {
        withCredentials([gitUsernamePassword(credentialsId: GIT_CREDENTIALS_ID)]) {
            def gitUrl = buildGitUrl("https://git.system.local/scm/${repo.projectName}/${repo.repoName}.git")
            sh "rm -rf ${repo.repoName}"
            sh "git clone ${gitUrl} --depth 1 -b '${targetBranch}'"
            dir(repo.repoName) {
                sh "./clean-feauture.sh ${serviceName} ${jiraTicket}"
                sh "./deploy-feature.sh ${serviceName} ${serviceSuffix} ${jiraTicket} ${imageName}"
                sh "git add envs/dev"
                String changedFiles = sh(script: "git diff --name-only ${targetBranch} origin/${targetBranch}", returnStdout: true).trim()
                if (changedFiles) {
                    echo "The following files changed:"
                    echo "${changedFiles}"
                    sh 'git push -u origin HEAD'
                    return true
                } else {
                    echo "No changes between the local and remote branches."
                    return false
                }
            }
        }
    }
}
