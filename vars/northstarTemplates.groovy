def multibranchTemplate = """
multibranchPipelineJob('$pipelineName') {
    branchSources {
            branchSource {
                source {
                    github {
                        id('$pipelineName')
                        repoOwner('$repoOwner')
                        repository('$repository')
                        credentialsId($githubCredentials)
                        buildOriginBranch(true)
                        buildOriginPRHead(true)
                        repositoryUrl('')
                        configuredByUrl(false)
                    }
                }
                strategy {
                allBranchesSame {
                        props {
                            suppressAutomaticTriggering {
                                strategy('INDEXING')
                                triggeredBranchesRegex('.*')
                            }
                        }
                    }
                }
            }
    }
    orphanedItemStrategy {
        discardOldItems {
        daysToKeep($orphanedItemStrategyDaysToKeep)
        numToKeep($orphanedItemStrategyNumToKeep)
        }
    }
    factory {
        workflowBranchProjectFactory {
        scriptPath($scriptPath)
        }
    }
    configure {
        def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
        traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
            strategyId($branchDiscoveryTraitStrategyId) // Enable support for discovering github branches on this repo
        }
        traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
            strategyId($repo.originPullRequestTraitStrategyId) // Enable support for discovering PullRequests to this github repo
        }
        traits << 'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait' {
            extension(class: 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout') {
                deleteUntrackedNestedRepositories($deleteUntrackedNestedRepositories)
            }
        }
    }
}
"""

def pipelineTemplate = """
pipelineJob('$pipelineName'){
    parameters{
        $parametersText
    }
    definition{
        cpsScm{
            scm{
                git{
                    remote{
                        github('$repoOwner/$repoName', 'https')
                        credentials('$githubCredentials')
                    }
                    branch('$branch')
                }
            }
            scriptPath('$scriptPath')
        }
    }
    logRotator {
        daysToKeep($logRotatorDaysToKeep)
        numToKeep($logRotatorNumToKeep)
    }
}
"""

def folderTemplate = """
folder('$folderName'){
    description('$description')
    authorization{
        $permissionsText
    }
    configure{
        it / 'properties' /'org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty'(plugin:"kubernetes@1.19.0") {
            'permittedClouds' {
                'string' '$buildCloud'
            }
        }
    }
}
"""

def parameterTemplate = """
$type('$name','$defaultValue','$description')
"""