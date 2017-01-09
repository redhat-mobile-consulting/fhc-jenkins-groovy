/**
    This file is a template groovy script used by Jenkins pipeline.
    It has following steps:
        1. checkout code
        2. git push to specific environment (branch) 
        3. create new tag according to version in package.json and datetime
        4. push tag to RHMAP git 
        5. stage the specific tag to the environment
        6. Mail deployment note
    This script should be parameterized 
*/

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
def userLogin=[
  target:env["Domain Url"],
  username: env["FH Login"],
  password: env["FH Password"]
]

//the cloud app id from RHMAP
def appId=env["Cloud App Id"]
//the node.js runtime to be staged to. e.g. node4
def runTime=env["Target Node Run Time"]
//the list of emails to send the deployment note for reference. multiple emails can be seperated by comma..e.g. aaa@b.com, ccc@b.com
def mailList=env["Release note email list"]
//email subject
def subject="${env['Project Name']} has been deployed to ${env['Target Environment']}"

def targetBranch=env['Target Environment']

def targetEnv=env['Target Environment']


//Script start
@Library("rhmap@master")
import com.redhat.rhmap.Cloud
def cloud = new Cloud()

node{
    stage "Prepare"
    cloud.setupNode(nodeName)
    cloud.checkoutCode(credentialId, cloudGitUrl, branchName)
    stage "Deploy"
    cloud.rhmapLogin(userLogin.target,userLogin.username,userLogin.password)
    def releaseNote=cloud.release(credentialId, targetBranch, appId,targetEnv, runTime)
    stage "Deploy Note"
    cloud.mailRelease(releaseNote, subject, mailList) 
}