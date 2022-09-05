#!/usr/bin/env groovy

def multibranchTemplate(data) {
    def template = """
    multibranchPipelineJob('""" + data.pipelineName + """') {
        branchSources {
                branchSource {
                    source {
                        github {
                            id('""" + data.pipelineName + """')
                            repoOwner('""" + data.repoOwner + """')
                            repository('""" + data.repository + """')
                            credentialsId('""" + data.githubCredentials + """')
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
            daysToKeep(""" + data.orphanedItemStrategyDaysToKeep + """)
            numToKeep(""" + data.orphanedItemStrategyNumToKeep + """)
            }
        }
        factory {
            workflowBranchProjectFactory {
            scriptPath('""" + data.scriptPath + """')
            }
        }
        configure {
            def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
            traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
                strategyId(""" + data.branchDiscoveryTraitStrategyId + """) // Enable support for discovering github branches on this repo
            }
            traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
                strategyId(""" + data.originPullRequestTraitStrategyId + """) // Enable support for discovering PullRequests to this github repo
            }
            traits << 'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait' {
                extension(class: 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout') {
                    deleteUntrackedNestedRepositories(""" + data.deleteUntrackedNestedRepositories + """)
                }
            }
        }
    }
    """
    return template;
}

def pipelineTemplate(data) {
    def template = """
    pipelineJob('""" + data.pipelineName + """'){
        parameters{
            """ + data.parametersText + """
        }
        definition{
            cpsScm{
                scm{
                    git{
                        remote{
                            github('""" + data.repoOwner + """/""" + data.repository + """', 'https')
                            credentials('""" + data.githubCredentials + """')
                        }
                        branch('""" + data.branch + """')
                    }
                }
                scriptPath('""" + data.scriptPath + """')
            }
        }
        logRotator {
            daysToKeep(""" + data.logRotatorDaysToKeep + """)
            numToKeep(""" + data.logRotatorNumToKeep + """)
        }
    }
    """
    return template
}

def folderTemplate(data) {
    def template = """
    folder('""" + data.folderName + """'){
        description('""" + data.description + """')
        authorization{
            """ + data.permissionsText + """
        }
        configure{
            it / 'properties' /'org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty'(plugin:"kubernetes@1.19.0") {
                'permittedClouds' {
                    """ + data.buildCloudsText + """
                }
            }
        }
    }
    """
    return template
}

def stringParameterTemplate(data) {
    def template = data.type + "('" + data.name + "','" + data.defaultValue + "','" + data.description + "')"
    return template
}

def booleanParameterTemplate(data) {
    def template = data.type + "('" + data.name + "'," + data.defaultValue + ",'" + data.description + "')"
    return template
}

def choiceParameterTemplate(data) {
    def template = data.type + "('" + data.name + "',['" + parameter.choices.join("','") + "'])"
    return template
}

def buildCloudTemplate(cloud) {
    def template = "'string' '" + cloud + "'"
    return template
}

def permissionTemplate(data){

    def permissionAccessLevels = [
    "readOnly": """
                'hudson.model.Item.Read',
                'hudson.model.View.Read'
                """,
    "fullAccess": """
                'hudson.model.Item.Build',
                'hudson.model.Item.Cancel',
                'hudson.model.Item.Configure',
                'hudson.model.Item.Discover',
                'hudson.model.Item.Read',
                'hudson.model.Item.Workspace',
                'hudson.model.Run.Replay',
                'hudson.model.View.Read'
                """
    ]
    
    def template = """
    permissions('""" + data.roleName + """', [ """ +
    permissionAccessLevels[data.accessLevel] +
    """])
    """
    return template;
}

return this;