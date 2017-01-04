/**
    This file is a template groovy script used by Jenkins pipeline.
    It has following steps:
        1. checkout code
        2. run npm install 
        3. run npm test
    This script does not need user interaction
*/

// Please provide following information

//credentialId is ssh key pairs credential's id stored in jenkins. This can be found (added) in credentials page in jenkins
def credentialId=""  
//the git url from RHMAP cloud app
def cloudGitUrl=""
//the branch to check out as source branch. e.g. develop
def branchName=""
//the configured node.js runtime name from node.js plugin in jenkins. Need node.js plugin and configure it in jenkins.
def nodeName=""


//Script start 
def cloud = fileLoader.fromGit('cloud.groovy', 
        'https://github.com/redhat-mobile-consulting/fhc-jenkins-groovy.git', 'master', null, '')
node {
    stage "Prepare"
        cloud.setupNode(nodeName)
        cloud.checkoutCode(credentialId, cloudGitUrl, branchName)
    stage "Build"
        cloud.npmInstall()
        cloud.npmtest() 
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