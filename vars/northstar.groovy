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


String getSeedJobDSL(yamlPath){

// Function to build seed job DSL from template, this function can be called as part of a pipeline to generate Job DSL scripts from 
// the template below - hydrated with values defined in the yaml files passed in.
//
// Plugin Dependencies: 
//        pipeline-utility-steps - used for the findFiles and readYaml methods
//
// Inputs:
//        [String yamlPath] - ant style file path to location of one or multiple yaml files with pipeline definitions, 
//                            supports wildcard filepaths to find multiple files - or individual files with multiple pipeline definitions
//                            eg. 'seed/jobs/**/repoList.yaml'  
// Returns: 
//        [String] - returns the DSL script generated for each pipeline defined in the yaml files as a string.

    def buildTemplate = { repo ->

        //Core template to define the pipeline, hydrated by values from passed in 'repo' Map
        def template = """
        multibranchPipelineJob('""" + repo.pipelineName + """') {
            branchSources {
                    branchSource {
                        source {
                            github {
                                id('""" + repo.pipelineName + """')
                                repoOwner('""" + repo.repoOwner + """')
                                repository('""" + repo.repository + """')
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
                                        triggeredBranchesRegex('.*')
                                    }
                                }
                            }
                        }
                    }
            }

            orphanedItemStrategy {
                discardOldItems {
                daysToKeep(""" + repo.orphanedItemStrategyDaysToKeep + """)
                numToKeep(""" + repo.orphanedItemStrategyNumToKeep + """)
                }
            }

            factory {
                workflowBranchProjectFactory {
                scriptPath('""" + repo.scriptPath + """')
                }
            }

            configure {
                def traits = it / sources / data / 'jenkins.branch.BranchSource' / source / traits
                traits << 'org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait' {
                strategyId(""" + repo.branchDiscoveryTraitStrategyId + """) // Enable support for discovering github branches on this repo
                }
                traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
                strategyId(""" + repo.originPullRequestTraitStrategyId + """) // Enable support for discovering PullRequests to this github repo
                }
                traits << 'jenkins.plugins.git.traits.CleanBeforeCheckoutTrait' {
                extension(class: 'hudson.plugins.git.extensions.impl.CleanBeforeCheckout') {
                    deleteUntrackedNestedRepositories(""" + repo.deleteUntrackedNestedRepositories + """)
                }
                }
            }
        }
        """

        return template
    } 

    //Default values for optional parameters - if these keys aren't present in the yaml definition the values here will be used instead.
    def defaultValues = [
        orphanedItemStrategyDaysToKeep: 30,
        orphanedItemStrategyNumToKeep: 30,
        branchDiscoveryTraitStrategyId: 1,
        originPullRequestTraitStrategyId: 2,
        deleteUntrackedNestedRepositories: true
    ]

    def populateRepoDefaults = { repo ->
        
        //Check for minimum required values of pipelineName, repoOwner, repository and scriptPath - if any are missing set validity of repo to false for error handling.
        repo.validity = true;
        if (!repo.pipelineName || !repo.repoOwner || !repo.repository || !repo.scriptPath){
            repo.validity = false;
            repo.validityReason = 'Missing required parameters pipelineName, repoOwner, repository, scriptPath'
        }

        //Replace unspecified parameters with values from default values map
        for (item in defaultValues){
            if(!repo[item.key]){
                repo[item.key] = item.value;
            }
        }

        return repo;
    }

    //Search for yaml files matching input file path, read the yaml files and generate DSL script for each defined repo in each file.
    def repoLists = findFiles(glob: yamlPath);
    def jobDefinitions = []; 
    for (repoList in repoLists){

        def data = readYaml file: repoList.path;
        for (repo in data.repos){
            repoData = populateRepoDefaults(repo);

            if (repoData.validity){
                def dslScript = buildTemplate(repoData);
                jobDefinitions.add(dslScript);
            } else {
                //Skip over invalid repo entries, log issue to console output
                sh 'echo "' + repoList.path + " invalid - " + repoData.validityReason + '"';
                continue;
            }
        }  
    }

    //Concatenate all generated Job DSL scripts into a single script and return.
    dslScript = jobDefinitions.join('')
    return dslScript
}

//If this library is not loaded 'implicitly', uncomment the line below:
//return this