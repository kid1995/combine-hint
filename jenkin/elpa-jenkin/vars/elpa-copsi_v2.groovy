import de.signaliduna.BitbucketRepo
import de.signaliduna.TargetSegment
import de.signaliduna.CopsiEnvironment

@Field
String DEPLOY_PROJECT =  "SDASVCDEPLOY"
@Field
String DEPLOY_REPO =  "elpa-elpa4"
@Field
String JIRA_PROJECT =  "ELPA4"

String deployTstFeature(String serviceName, String serviceSuffix, String imageName) {
    
    def branchName = branchNameWithoutPrefix()
    def branchNameStrArray = branchName.split('-')
    if (branchNameStrArray.size() >= 2 && branchNameStrArray.contains(JIRA_PROJECT)) {
        def jiraIssue = "${branchNameStrArray[0]}-${branchNameStrArray[1]}"
        echo "Built Image: ${imageName}"

        si_copsi.createChangeAsPullRequest(
                new BitbucketRepo(DEPLOY_PROJECT, DEPLOY_REPO),
                "autodeploy/${branchName}-job-${BUILD_NUMBER}",
                "${CopsiEnvironment.nop}",
                [:],
                {
                    echo "Führe clean-feature.sh und deploy-feature.sh aus für Feature: ${jiraIssue}"
                    sh "chmod +x ./clean-feature.sh"
                    sh "chmod +x ./deploy-feature.sh"
                    sh "./clean-feature.sh ${serviceName} ${jiraIssue}"
                    sh "./deploy-feature.sh ${serviceName} ${serviceSuffix} ${jiraIssue} ${imageName}"
                    sh "git add ./envs/dev"
                    return "${jiraIssue}: Deploy ${serviceName} mit Image ${imageName}"
                }
        )
    } else {
            echo "ERROR branchName branch name is invalid: ${branchName} - it should be ELPA4-xxxx-<feature-info>"
    }       
}

/** 
 * @returns branch name without any prefix
 */
public String branchNameWithoutPrefix() {    
    def fullBranchNameArray = si_git.branchName().split("/")
    return fullBranchNameArray[fullBranchNameArray.size()-1]
}