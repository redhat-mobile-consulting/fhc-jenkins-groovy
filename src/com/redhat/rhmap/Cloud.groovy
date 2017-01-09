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
def rhmapLogin(target, username, password){
  echo "RHMAP: Login to ${target}"
  sh "fhc target ${target}"
  sh "fhc login ${username} ${password}"
}
def pushCode(credentialsId, branchName){
  echo "Push current code base to branch: ${branchName}"
  sshagent(["${credentialsId}"]) {
      def curBranch=sh returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD'
      curBranch=curBranch.trim()
      sh "git push origin ${curBranch}:${branchName} --force"
  }
}
def rhmapStage(appId, env,refType, refVal, refHash, runTime){
  echo "RHMAP: stage cloud app in ${env} from ref: ${refVal} app: ${appId} to nodejs runtime: ${runTime}"
  sh "fhc app stage --app=${appId} --env=${env} --gitRef.type=${refType} --gitRef.value=${refVal} --gitRef.hash=${refHash} --runtime=${runTime}"
}
def rhmapStageBranch(appId,env,branchName,runTime){
  rhmapStage (appId,env,"branch",branchName,"HEAD",runTime)
} 
def rhmapStageTag(appId,env,tag,runTime){
  rhmapStage (appId,env,"tag",tag,tag,runTime)
} 
def getVersion(){
  def ver=sh returnStdout: true, script: 'node -e "console.log(require(\'./package.json\').version)"'
  ver=ver.trim()
  return ver
}
def release(credentialsId,name, appId, env, runTime){
  def dateTime=new Date().format("yyyyMMddHHmm")
  def ver=getVersion()
  def tag="${name}-${ver}-${dateTime}"
  def note
  def curCommit="N/A"
  sshagent(["${credentialsId}"]) {
      def hasRemote=sh returnStdout: true, script: 'git ls-remote origin uat'
      
      if (hasRemote.size()>0){
          sh "git fetch origin ${name}"
          note=sh returnStdout: true, script: "git shortlog origin/${name}..HEAD"
          curCommit=sh returnStdout: true, script: "git rev-parse origin/${name}"
          curCommit=curCommit.trim()
      }else{
          note=sh returnStdout: true, script: 'git shortlog HEAD'
      }
      pushCode(credentialsId,name)
      sh "git tag -f ${tag}"
      sh "git push origin ${tag} --force"
  }
  // input "Deploy version ${ver} to RHMAP UAT env?\nChanges:\n${uatNote}"
  rhmapStageTag(appId, env, tag, runTime)
  // sh "fhc app stage --app=2ur3nkvlj3f6torrlobo3smm --env=stage --gitRef.type=tag --gitRef.value=${uatTag} --gitRef.hash=${uatTag} --runtime=node4"
  def fileName="releaseNote/${tag}.txt"
  writeFile file: "${fileName}", text: "${note}"
  archiveArtifacts artifacts: "${fileName}", excludes: null
  return [
    note:note,
    startCommit:curCommit,
    tag:tag
  ]
}
def mailRelease(releaseNote, subject, toList){
  def body="Build URL:\n${env.BUILD_URL}\n\nCommit Range:${releaseNote.startCommit}..${releaseNote.tag}\n\nTag name: ${releaseNote.tag}\n\nRelease Note:\n${releaseNote.note}"
  mail bcc: '', body: "${body}", cc: '', from: '', replyTo: '', subject: "${subject}", to: "${toList}"
}
