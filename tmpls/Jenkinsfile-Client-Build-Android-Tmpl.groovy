// Please provide following information in Jenkins Pipeline Parameters

//credentialId is ssh key pairs credential's id stored in jenkins. This can be found (added) in credentials page in jenkins
def credentialId=env["Credential Id"] 
// RHMAP login 
def fhCredential=env["FH Login"] 
def fhTarget=env["Domain Url"]
//the git url from RHMAP cloud app
def clientGitUrl=env["Client Git Url"]
//the branch to check out as source branch. e.g. develop
def branchName=env["Branch Name"]
//the configured node.js runtime name from node.js plugin in jenkins. Need node.js plugin and configure it in jenkins.
def nodeName=env["Jenkins Node Name"]

def deployEnv=env["Deployment Environment"]

def appId=env["Client App Id"]

def projectId=env["Project Id"]

def cloudAppId=env["Cloud App Id"]

def projectName=env["Project Name"]

def emailList=env["Email List"]

//Script start 
@Library("rhmap@master")
import com.redhat.rhmap.Client
def client = new Client()
node {
    stage "Prepare"
        client.setupNode(nodeName)
        client.checkoutCode(credentialId, cloudGitUrl, branchName)
        client.rhmapLogin(fhTarget,fhCredential)
    stage "Unit Test"
    stage "Build"
        def dateTime=new Date().format("yyyyMMddHHmmss")
        def output=client.buildAndroid(appId,projectId,cloudAppId,deployEnv,branchName,dateTime)
        def tag=client.genTag(deployEnv,dateTime,"android")
        def buildNote=client.tagCode(tag,credentialId)
        client.emailBuild(emailList,projectName,deployEnv, appId,projectId, output, tag, cloudAppId) 
    stage "User Acceptance Test"
    
}
