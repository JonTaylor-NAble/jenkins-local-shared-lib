#!/usr/bin/env groovy

def call(planPath){

  sh 'terraform -chdir=./cicd/pipelines/terraform/ show -json ' + planPath + ' > tfPlan.log'

  def resultString = readFile(file: 'tfPlan.log');
  def results = resultString.split('\n')

  def enhancedWarning = false;
  def triggeringChange;
  def output;

  for (def result in results){
    try{
      output = readJSON text:result;
    } catch (Exception ex){
    }
  }

  if (output){
    if (output.resource_changes){
      for (def resource_change in output.resource_changes){
        if (resource_change.change){
          if (resource_change.change.actions.indexOf('update') > -1){

            def manifestBeforeStr = resource_change.change.before.manifest;
            def manifestAfterStr = resource_change.change.after.manifest;

            def manifestBefore = readJSON text:manifestBeforeStr;
            def manifestAfter = readJSON text:manifestAfterStr;

            if (manifestBefore.kind == 'Jenkins'){
              if (manifestBefore.spec && manifestBefore.spec.master){
                def compBefore = manifestBefore.spec.master;
                def compAfter = manifestAfter.spec.master;

                if (compBefore != compAfter){
                  enhancedWarning = true;
                  triggeringChange = compAfter;
                  echo "Master node config is changed"
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

  sh 'rm -f tfPlan.log'
  return enhancedWarning;
}