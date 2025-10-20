import de.signaliduna.BitbucketRepo
import de.signaliduna.TargetSegment
import de.signaliduna.CopsiEnvironment

@Field
String DEPLOY_PROJECT =  "SDASVCDEPLOY"
@Field
String DEPLOY_REPO =  "elpa-elpa4"
@Field
String JIRA_PROJECT_PREFIX =  "ELPA4"

String deployTstFeature(String serviceName, String imageName) {    
    def branchName = branchNameWithoutPrefix()
    def branchNameStrArray = branchName.split('-')
    BitbucketRepo deploymentRepository = new BitbucketRepo(DEPLOY_PROJECT, DEPLOY_REPO)
    def pullRequestAttributes = [
        title: "Autodeploy ${JIRA_TICKET} ${BUILD_NUMBER}",
        description: "Autodeploy for ${featureName}"
    ]   

    if (branchNameStrArray.size() >= 2 && branchNameStrArray[0] == JIRA_PROJECT_PREFIX) {
        def jiraTicket = "${branchNameStrArray[0]}-${branchNameStrArray[1]}"
        echo "Built Image: ${imageName}"

        def prId = si_copsi.createChangeAsPullRequest(
                deploymentRepository,
                "autodeploy/${branchName[0..32]}-job-$BUILD_NUMBER",
                "${CopsiEnvironment.nop}",
                pullRequestAttributes,
                {
                    echo "Führe clean-feature.sh und deploy-feature.sh aus für Feature: ${jiraTicket}"
                    sh "chmod +x ./clean-feature.sh"
                    sh "chmod +x ./deploy-feature.sh"
                    sh "./clean-feature.sh ${serviceName} ${jiraTicket}"
                    sh "./deploy-feature.sh ${serviceName} ${jiraTicket} ${imageName}"
                    sh "git add ./envs/dev"
                    return "${jiraTicket}: Deploy ${serviceName} mit Image ${imageName}"
                }
        )

        boolean abortBuildOnError = true
		boolean deleteSourceBranch = true
		def	timeout = 60
		def mergeResult = si_copsi.waitForMergeChecksAndMerge(deploymentRepository, prId, abortBuildOnError, deleteSourceBranch, timeout) ? "success" : "failed"

		echo "Merge Feature Deploy ${mergeResult}"
    } else {
            echo "ERROR: Branch name '${branchName}' is invalid. For automatic deployment, the branch name must be in the format '${JIRA_PROJECT_PREFIX}-XXXX' (e.g., '${JIRA_PROJECT_PREFIX}-1234')"
    }       
}

/** 
 * @returns branch name without any prefix
 */
public String branchNameWithoutPrefix() {    
    def fullBranchNameArray = si_git.branchName().split("/")
    return fullBranchNameArray.last()
}