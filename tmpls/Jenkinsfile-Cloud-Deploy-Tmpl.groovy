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
def credentialId=""  
//the git url from RHMAP cloud app
def cloudGitUrl=""
//rhmap login
def userLogin=[
  target:"<Domain URL>",
  username: "Username or email that can deploy cloud app",
  password: "user's password'"
]
//the configured node.js runtime name from node.js plugin in jenkins. Need node.js plugin and configure it in jenkins.
def nodeName=""
//the cloud app id from RHMAP
def appId=""
//the node.js runtime to be staged to. e.g. node4
def runTime=""
//the list of emails to send the deployment note for reference. multiple emails can be seperated by comma..e.g. aaa@b.com, ccc@b.com
def mailList=""
//email subject
def subject="<Project name> has been deployed to ${env['Target Environment']}"
//environments on RHMAP. the branchName is which branch to push the new code and envName is environemtn name defined in RHMAP. Change according to real situation.
def envs=[
  develop:[
    branchName: 'develop',
    envName: 'develop'
  ],
  test:[
    branchName: 'test',
    envName: 'test'
  ],
  uat:[
    branchName: 'uat',
    envName: 'uat'
  ],
  live:[
    branchName: 'live',
    envName: 'live'
  ]
]
//The source environment where to load source code
def fromEnv=envs[env["Source Environment"]]
//the target environment to deploy the code on RHMAP 
def targetEnv=envs[env["Target Environment"]]



//Script start
def cloud = fileLoader.fromGit('cloud.groovy', 
        'https://github.com/redhat-mobile-consulting/fhc-jenkins-groovy.git', 'master', null, '')



node{
    stage "Prepare"
    cloud.setupNode(nodeName)
    cloud.checkoutCode(credentialId, cloudGitUrl, branchName)
    stage "Deploy"
    cloud.rhmapLogin(userLogin.target,userLogin.username,userLogin.password)
    def releaseNote=cloud.release(credentialId, targetEnv.branchName, appId,targetEnv.envName, runTime)
    stage "Deploy Note"
    cloud.mailRelease(releaseNote, subject, mailList) 
}