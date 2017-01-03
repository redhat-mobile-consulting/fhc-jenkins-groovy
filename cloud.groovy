
def setupNode(toolName){
  echo "Setup Node.JS"
  def nodeHome= tool "${toolName}"
  env.PATH = "${nodeHome}/bin:${env.PATH}"
}
def checkoutCode(credentialsId, gitUrl, branchName){
  echo "Check out code base from : ${gitUrl} branch: ${branchName}"
  git branch: "${branchName}", credentialsId: "${credentialsId}", url: "${gitUrl}"
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
return this
node {
    
    stage "Prepare"
        setupNode("4.4.3")
        checkoutCode('06353c33-34c2-4d72-b461-889c7828a46b', 'git@git.eu.feedhenry.com:gp-online/GPOnline-GPOnline-Cloud.git', 'develop')
        rhmapLogin("https://gp-online.eu.feedhenry.com", "jenkins", "Rhmapjenkins1")
    stage "Build"
        echo "Run npm install"
        sh 'npm install .'
        echo "Run npm test"
        sh 'npm test'   
}
 
stage "Deploy(Test)"
input 'Deploy to RHMAP test env?'

node{   
    pushCode("06353c33-34c2-4d72-b461-889c7828a46b", "test")
    rhmapStageBranch("2ur3nkvlj3f6torrlobo3smm", "qa", "test", "node4")
}

    stage "Accept Test"
        input 'Run accept test on test env?'
input "Deploy to RHMAP UAT env?"
node{
    stage "Deploy(UAT)"
        def uatRelease=release("06353c33-34c2-4d72-b461-889c7828a46b","uat", "2ur3nkvlj3f6torrlobo3smm", "stage" ,"node4")
        mail bcc: '', body: "Build URL:\n${env.BUILD_URL}\n\nCommit Range:${uatRelease.startCommit}..${uatRelease.tag}\n\nTag name: ${uatRelease.tag}\n\nRelease Note:\n${uatRelease.note}", cc: '', from: '', replyTo: '', subject: "GPOnline has been released to UAT", to: 'kxiang@redhat.com'
    stage "Deploy(Live)"
        def liveTag="live-${ver}-${dateTime}"
        def liveNote
        curCommit="N/A"
        sshagent(['06353c33-34c2-4d72-b461-889c7828a46b']) {
            def hasRemote=sh returnStdout: true, script: 'git ls-remote origin live'
            
            if (hasRemote.size()>0){
                sh 'git fetch origin live'
                liveNote=sh returnStdout: true, script: 'git shortlog origin/live..HEAD'
                curCommit=sh returnStdout: true, script: 'git rev-parse origin/live'
                curCommit=curCommit.trim()
            }else{
                liveNote=sh returnStdout: true, script: 'git shortlog HEAD'
            }
        }
        input "Deploy version ${ver} to RHMAP LIVE env?\nChanges:\n${liveNote}"
        sshagent(['06353c33-34c2-4d72-b461-889c7828a46b']) {
            sh 'git push origin develop:live'
            sh "git tag -f ${liveTag}"
            sh "git push origin ${liveTag} --force"
        }
        sh "fhc app stage --app=2ur3nkvlj3f6torrlobo3smm --env=gpo-live --gitRef.type=tag --gitRef.value=${liveTag} --gitRef.hash=${liveTag} --runtime=node4"
        fileName="releaseNote/${liveTag}.txt"
        writeFile file: "${fileName}", text: "${liveNote}"
        archiveArtifacts artifacts: "${fileName}", excludes: null
        mail bcc: '', body: "Build URL:\n${env.BUILD_URL}\n\n Commit Range: ${curCommit}..${liveTag} \n\nTag name: ${liveTag}\n\nRelease Note:\n${liveNote}", cc: '', from: '', replyTo: '', subject: "GPOnline ${liveTag} has been deployed to LIVE", to: 'kxiang@redhat.com'
}