package com.redhat.rhmap


def setupNode(toolName){
  echo "Setup Node.JS"
  def nodeHome= tool "${toolName}"
  env.PATH = "${nodeHome}/bin:${env.PATH}"
}
def checkoutCode(credentialsId, gitUrl, branchName){
  echo "Check out code base from : ${gitUrl} branch: ${branchName}"
  git branch: "${branchName}", credentialsId: "${credentialsId}", url: "${gitUrl}"
}
def npmInstall(){
  echo "Run npm install"
  sh 'npm install .' 
}
def npmTest(){
  echo "Run npm test"
  sh 'npm test' 
}
def npmRun(cmd){
  echo "Run npm cmd: ${cmd}"
  sh "npm run ${cmd}" 
}
def npmAcceptTest(){
  npmRun('accept-test')
}
def rhmapLogin(target, fhCredential){
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: fhCredential,
                          usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
      echo "RHMAP: Login to ${target}"
      sh "fhc target ${target}"
      sh "fhc login ${USERNAME} ${PASSWORD}"
  }
}

def mailRelease(releaseNote, subject, toList){
  def body="Build URL:\n${env.BUILD_URL}\n\nCommit Range:${releaseNote.startCommit}..${releaseNote.tag}\n\nTag name: ${releaseNote.tag}\n\nRelease Note:\n${releaseNote.note}"
  mail bcc: '', body: "${body}", cc: '', from: '', replyTo: '', subject: "${subject}", to: "${toList}"
}

def buildAndroid(clientAppId,projectId, cloudAppId, targetEnv, branchName,dateTime){
  
  def output=sh returnStdout: true, script: "fhc build app=${clientAppId} tag=0.0.1-ci-${targetEnv}-${dateTime} destination=android git-branch=${branchName} project=${projectId} cloud_app=${cloudAppId} environment=${targetEnv}"
  return output  
}

def genTag(targetEnv,dateTime,destination){
  return "${destination}-${targetEnv}-${dateTime}"
}

def emailBuild(emailList, projectName, targetEnv, appId, projectId, buildOutput, gitTag, cloudAppId, buildNote){
  def body="""
 Jenkins Build URL: ${env.BUILD_URL}

 Project Name: ${projectName}

 App Id: ${appId}

 Project Id: ${projectId}

 Tag: ${gitTag}

 Connection Cloud App Id: ${cloudAppId}

 Connection Cloud Environment: ${targetEnv}

 Changes Note:

 ${buildNote}

 Build Url:

 ${buildOutput}  
 """
  mail bcc:'', body: body, cc: '', from: '', replyTo: '', subject: "[${projectName}] Build android", to: "${emailList}" 
}

def tagCode(tag,credentialId,branchName){
    def note;
    sshagent([credentialId]) {
        def hasRemote=sh returnStdout: true, script: 'git ls-remote origin build'
        if (hasRemote.size()>0){
            sh 'git fetch origin build'
            note=sh returnStdout: true, script: 'git shortlog origin/build..HEAD'
            curCommit=sh returnStdout: true, script: 'git rev-parse origin/build'
            curCommit=curCommit.trim()
        }else{
            note=sh returnStdout: true, script: 'git shortlog HEAD'
        }
        sh "git push origin ${branchName}:build"
        sh "git tag -f ${tag}"
        sh "git push origin ${tag} --force"
    }
    return note; 
}