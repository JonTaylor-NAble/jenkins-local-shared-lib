#!/usr/bin/env groovy

// Library: 
//          This library includes a collection of methods used in NorthStar pipelines, methods can be called by invoking northstar.<methodname>([parameters])
//          Each method should be commented with it's expected purpose, inputs, outputs, and plugin dependencies at the top level within the method.


boolean checkForJenkinsMasterUpdates(planFilePath){

// Function to determine if a provided terraform plan file has planned changes to a Jenkins resource yaml that would trigger a pod restart as per the Jenkins CRD schema:
// https://jenkinsci.github.io/kubernetes-operator/docs/getting-started/latest/schema/
//
// Plugin Dependencies: 
//        pipeline-utility-steps - used for readJSON method
//
// Inputs:
//        [String planFile]: Relative file path of a terraform plan file in JSON format (eg. generated by 'terraform plan -out example.plan -json')
//
// Returns: 
//        [boolean] a boolean value where true indicates that the jenkins master pod properties are in the plan to be changed.


    boolean enhancedWarning = false;
    def triggeringChange;
    def output;

    // Call 'terraform show' on plan file with JSON output flag and capture output log as a file
    sh 'terraform show -json ' + planFilePath + ' > tfPlan.log'
    
    // Read the file as a string and split on newlines to seperate any additional contents
    def resultString = readFile(file: 'tfPlan.log');
    def results = resultString.split('\n')

    // For each output line, attempt to parse to JSON - take the first valid JSON line.
    for (def result in results){
        try{
        output = readJSON text:result;
        } catch (Exception ex){
        }
    }

    //Series of checks to see if this terraform plan item involves the jenkins > spec > master config
    if (output){
        if (output.resource_changes){
        for (def resource_change in output.resource_changes){
            if (resource_change.change){
            if (resource_change.change.actions.indexOf('update') > -1){

                //Read the before and after manifests for the master pod properties from JSON into Maps - and compare maps for any changes.

                def manifestBeforeStr = resource_change.change.before.manifest;
                def manifestAfterStr = resource_change.change.after.manifest;

                def manifestBefore;
                def manifestAfter;

                try{
                    manifestBefore = readJSON text:manifestBeforeStr;
                    manifestAfter = readJSON text:manifestAfterStr;
                } catch (Exception ex){
                    echo "Invalid JSON in resource_change manifests"
                    return false;
                }


                if (manifestBefore.kind == 'Jenkins'){
                    if (manifestBefore.spec){
                        //Check if there have been changes in the 'spec.master.*' node of the resource.
                        if (manifestBefore.spec.master){

                            def compMasterBefore = manifestBefore.spec.master;
                            def compMasterAfter = manifestAfter.spec.master;

                            // If before and after master node manifests are not identical, 
                            if (compMasterBefore != compMasterAfter){
                            enhancedWarning = true;
                            triggeringChange = compMasterAfter;
                            } 

                        }

                        //Check if a seed job has been removed/renamed as this will also trigger a restart
                        if (manifestBefore.spec.seedJobs){
                            for (def seedJobBefore in manifestBefore.spec.seedJobs){
                                def found = false;
                                for (def seedJobAfter in manifestAfter.spec.seedJobs){
                                    if (seedJobBefore.id == seedJobAfter.id){
                                        found = true;
                                        break
                                    }
                                }
                                if (!found){
                                    enhancedWarning = true;
                                    break;
                                }
                            }
                        }
                    }
                } 
            }
            }
        }
        }
    } else {
        echo "Invalid TF Plan JSON"
    }

    return enhancedWarning;
}

String getSeedJobTemplate(){

// TODO: Function to build seed jobs from input
//
// Plugin Dependencies: 
//        TODO: Plugin dependencies
//
// Inputs:
//        TODO: Input descriptions
//
// Returns: 
//        TODO: Output description

    def repos = new FileNameFinder().getFileNames('seed/jobs/','**/repos.yaml')

    for (repo in repos){
        println repo
    }

    def template = """
        multibranchPipelineJob('terraform-pipeline') {
            branchSources {
                    branchSource {
                        source {
                            github {
                                id('terraform-pipeline')
                                repoOwner('JonTaylor-NAble')
                                repository('jenkins-local-pipelines')
                                credentialsId('github-account')
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
                                    }
                                }
                            }
                        }
                    }
            }

            orphanedItemStrategy {
                discardOldItems {
                daysToKeep(30)
                numToKeep(30)
                }
            }

            factory {
                workflowBranchProjectFactory {
                scriptPath('cicd/pipelines/terraform/terraform.groovy')
                }
            }

            configure {
                def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
                traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
                strategyId(1) // Enable support for discovering github branches on this repo
                }
                traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
                strategyId(2) // Enable support for discovering PullRequests to this github repo
                }
                traits << 'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait' {
                extension(class: 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout') {
                    deleteUntrackedNestedRepositories(true)
                }
                }
            }
        }"""

        return template
}

//If this library is not loaded 'implicitly', uncomment the line below:
//return this