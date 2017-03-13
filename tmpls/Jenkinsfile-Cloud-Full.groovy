// Please provide following information
//credentialId is ssh key pairs credential's id stored in jenkins. This can be found (added) in credentials page in jenkins
def credentialId=env["Credential Id"]
//the git url from RHMAP cloud app
def cloudGitUrl=env["Cloud Git Url"]
//the branch to check out as source branch. e.g. develop
def branchName=env["Branch Name"]
//the configured node.js runtime name from node.js plugin in jenkins. Need node.js plugin and configure it in jenkins.
def nodeName=env["Jenkins Node Name"]
//rhmap login
def fhTarget=env["Domain Url"]

def fhCredential=env["FH Login"]

//the cloud app id from RHMAP
def appId=env["Cloud App Id"]
//the node.js runtime to be staged to. e.g. node4
def runTime=env["Target Node Run Time"]
//the list of emails to send the deployment note for reference. multiple emails can be seperated by comma..e.g. aaa@b.com, ccc@b.com
def mailList=env["Release note email list"]

def envList=env['Cloud Environments']



//Script start
@Library("rhmap@master")
import com.redhat.rhmap.Cloud
def cloud = new Cloud()

node{
    stage ("Prepare"){
      cloud.setupNode(nodeName)
      cloud.checkoutCode(credentialId, cloudGitUrl, branchName)
    }
    stage("Build"){
      cloud.npmInstall() 
    }
    stage("Test(Unit)"){
      sh 'npm test'
    }
}
def envName=input message: 'Please choose an environment to deploy', ok: 'Deploy', parameters: [[$class: 'ChoiceParameterDefinition', choices: envList, description: '', name: 'Cloud Environments']]
//email subject
def subject="${env['Project Name']} has been deployed to ${envName}"
node {
    stage ("Deploy"){
      cloud.rhmapLogin(fhTarget,fhCredential)
      def releaseNote=cloud.release(credentialId, envName, appId,envName, runTime)
    }
    stage ("Deploy Note"){
      cloud.mailRelease(releaseNote, subject, mailList) 
    }
}