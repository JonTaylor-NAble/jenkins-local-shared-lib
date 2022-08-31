#!/usr/bin/env groovy
import groovy.transform.Field

@Field
def multibranchTemplate = """
multibranchPipelineJob('<% print pipelineName %>') {
    branchSources {
            branchSource {
                source {
                    github {
                        id('<% print pipelineName %>')
                        repoOwner('<% print repoOwner %>')
                        repository('<% print repository %>')
                        credentialsId(<% print githubCredentials %>)
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
        daysToKeep(<% print orphanedItemStrategyDaysToKeep %>)
        numToKeep(<% print orphanedItemStrategyNumToKeep %>)
        }
    }
    factory {
        workflowBranchProjectFactory {
        scriptPath(<% print scriptPath %>)
        }
    }
    configure {
        def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
        traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
            strategyId(<% print branchDiscoveryTraitStrategyId %>) // Enable support for discovering github branches on this repo
        }
        traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
            strategyId(<% print originPullRequestTraitStrategyId %>) // Enable support for discovering PullRequests to this github repo
        }
        traits << 'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait' {
            extension(class: 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout') {
                deleteUntrackedNestedRepositories(<% print deleteUntrackedNestedRepositories %>)
            }
        }
    }
}
"""
@Field
def pipelineTemplate = """
pipelineJob('<% print pipelineName %>'){
    parameters{
        <% print parametersText %>
    }
    definition{
        cpsScm{
            scm{
                git{
                    remote{
                        github('<% print repoOwner %>/<% print repoName %>', 'https')
                        credentials('<% print githubCredentials')
                    }
                    branch('<% print branch %>')
                }
            }
            scriptPath('<% print scriptPath %>')
        }
    }
    logRotator {
        daysToKeep(<% print logRotatorDaysToKeep %>)
        numToKeep(<% print logRotatorNumToKeep %>)
    }
}
"""
@Field
def folderTemplate = """
folder('<% print folderName %>'){
    description('<% print description %>')
    authorization{
        <% print permissionsText %>
    }
    configure{
        it / 'properties' /'org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty'(plugin:"kubernetes@1.19.0") {
            'permittedClouds' {
                'string' '<% print buildCloud' %>
            }
        }
    }
}
"""
@Field
def parameterTemplate = """
<% print type %>('<% print name %>','<% print defaultValue %>','<% print description %>')
"""

return this;