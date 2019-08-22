import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic


PrNumber = ghprbPullId
PrCommitAuthor = ghprbActualCommitAuthor
PrCommitAuthorEmail = ghprbActualCommitAuthorEmail



def dockerImageBuild(Map dockerInfo){
    withCredentials([usernameColonPassword(credentialsId: dockerInfo['repoInfo']['credentialsId'], variable: 'USERANDPSD')]){
        docker.withRegistry(dockerInfo['repoInfo']['dockerArtRepoUrl'],dockerInfo['repoInfo']['credentialsId']){
            println("Login into the docker artifactory succ")
            
            dir("src/images"){
                sh "cp ../target/universal/accesshub4atlas-1.0.1.zip ./"
                println("++++++++++Begin to build Accesshub docker image++++++++++")
                images = docker.build("${dockerInfo['accesshubImagesInfo']['imageName']}")
                println(images)
                images.push("${dockerInfo['accesshubImagesInfo']['tag']}")

            }

        }
    }
}

def doHelmAction4Accesshub(){
    withCredentials([usernamePassword(credentialsId: 'USERANDPSD4SALESCDAFID', usernameVariable: 'ARTIFACTORYUSER', passwordVariable: 'ARTIFACTORYPWD')
                     ]){
                            def imageUser = ARTIFACTORYUSER
                            def imagePwd = ARTIFACTORYPWD
                            def pr = PrNumber

                            def parameters = """
                                        image.username=${imageUser}, image.password=${imagePwd},image.tag=${pr}
                            """

                            parameters = parameters.replaceAll("\\s*", "")
                            cmd = "helm upgrade accesshub-dev-${pr} . --namespace accesshub-dev-${pr} -i --set ${parameters} -f values-dev.yaml"

                            sh cmd
                     }
}



def gitHubRepoInfo(String type){
    def gitRepoInfo = [:]
    if(type == "source_code"){
        print("This is build repo")
        gitRepoInfo = [
            'branch': [
                'name': "develop"
            ],
            'credentialsId': [
                'name': "MJHSSHKEY4IBMGITHUB"
            ],
            'giturl': [
                'name': "Global-CRM-Platforms-API/accessHubIntegration.git"
            ]
        ]
    } else if (type == "helm_code"){
        println("This is devops repo")
        gitRepoInfo = [
            'branch': [
                'name': "master"
            ],
            'credentialsId': [
                'name': "MJHSSHKEY4IBMGITHUB"
            ],
            'giturl': [
                'name': "CRMK8sDevOps/DevOps.git"
            ]
        ]
    } else {
        println("Please input right repo info")
        return gitRepoInfo
    }
    println gitRepoInfo
    return gitRepoInfo
}

def fetchSourceCode(Map gitHubRepoInfo, String codeDir){
    println("Being to fetch souce code")
    if(codeDir == "source_code"){
        println "code directory is src"
        targetDir = "src"
            try{
                    checkout(
                        [
                            $class: 'GitSCM', 
                            branches: [
                                [
                                    name: "+refs/remotes/origin/pr/${PrNumber}"
                                    ]
                                ], 
                            doGenerateSubmoduleConfigurations: false, 
                            extensions: 
                                [
                                    [
                                        $class: 'PreBuildMerge', 
                                        options: 
                                                [
                                                    fastForwardMode: 'FF', 
                                                    mergeStrategy: 'DEFAULT',
                                                    mergeRemote: 'origin', 
                                                    mergeTarget: 'master'
                                                ]
                                    ],
                                    [
                                        $class: 'UserIdentity', 
                                        email: 'devops008@sina.com', 
                                        name: 'xiaomage'
                                    ],
                                    [
                                        $class: 'RelativeTargetDirectory', 
                                        relativeTargetDir: targetDir
                                    ]
                                ], 
                            submoduleCfg: [], 
                            userRemoteConfigs: [
                                [
                                    credentialsId: "${gitHubRepoInfo['credentialsId']['name']}", 
                                    name: 'origin', 
                                    refspec: "+refs/pull/${PrNumber}/head:+refs/remotes/origin/pr/${PrNumber} +refs/heads/*:refs/remotes/origin/*", 
                                    url: "git@github.ibm.com:${gitHubRepoInfo['giturl']['name']}"
                                    ]
                                ]
                            ]
                        )
                } catch(Exception e){
                    println "could not fetch source code succ"
                }

    } else if(codeDir == "helm_code"){
        println "code directory is helm"
        targetDir = "helm"
        try{
            checkout(
                [
                    $class: 'GitSCM', 
                    branches: [
                        [
                            name: "*/master"
                            ]
                        ], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: targetDir]], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [
                        [
                            credentialsId: "MJHSSHKEY4IBMGITHUB", 
                            name: 'origin', 
                            refspec: "+refs/heads/master:+refs/remotes/origin/master", 
                            url: "git@github.ibm.com:CRMK8sDevOps/DevOps.git"
                            ]
                        ]
                    ]
                )
        } catch(Exception e){
                    println "could not fetch helm code succ"
        }
    } else {
        println "code directory is null"
        targetDir = null
    }


}

def getRepoInfo(){
    withCredentials([string(credentialsId: 'CRMCTSTOKEN4GITHUB', variable: 'GITHUBTOKEN')]){
        githubProperty = [
            githubApiUrl : 'https://github.ibm.com/api/v3/',
            githubApiToken : GITHUBTOKEN,
            repository: "Global-CRM-Platforms-API/accessHubIntegration"
        ]
    }
    return githubProperty
}



def sonarInfo = [
    sourcePath : "app/",
    hostUrl : "http://sonarqube-svc.sonar:9000/sonar",
    loginKey : "728172d82559e523503a3e38cc9bb3b5d4764223",
    javaBinariesPath: "target/scala-2.12/classes/"
]

def getSonarReport(){

    def projectKey = "accesshub4atlas"
    def conn = "http://sonarqube-svc.sonar:9000/sonar/api/qualitygates/project_status"
    def param="projectKey="+ projectKey
    conn+=param
    def getSonarResultApi = new URL(conn)

    HttpURLConnection connection = getSonarResultApi.openConnection()
    connection.setRequestMethod('GET')
    connection.setDoOutput(true)
    connection.setRequestProperty("Content-Type", "application/json")
    connection.connect()

    content = new JsonSlurperClassic().parse(new InputStreamReader(connection.getInputStream(),'UTF-8'))

    if(content['projectStatus']['status'] == 'OK'){
        println "The result is ok"
    } else {
        println "The result is failure"
    }
    connection.disconnect()
}

def sonarCheck(Map sonarInfo){
    println "Being to sonar scanner"
    def cmd = """
             sbt -Dsonar.sources=${sonarInfo['sourcePath']} -Dsonar.host.url=${sonarInfo['hostUrl']} -Dsonar.login=${sonarInfo['loginKey']} -Dsonar.java.binaries=${sonarInfo['javaBinariesPath']} sonarScan
          """
    sh cmd
}


def label = "jnlp-${UUID.randomUUID().toString()}"

podTemplate(label: label,
        serviceAccount: 'cts-jenkins-sa',
        namespace: 'jenkins',
        containers: [
            containerTemplate(
                name: 'jnlp',
                image: 'gbyukg/docker-jnlp-slave:1.0',
                args: '${computer.jnlpmac} ${computer.name}'
                ),
            containerTemplate(
                name: 'sbt',
                image: 'dllhb/sbt-sonarqube:latest',
                command: '/bin/sh -c',
                args: 'cat',
                ttyEnabled: true
                ),
            containerTemplate(
                name: 'docker',
                image: 'docker:17.09.0-ce',
                command: '/bin/sh -c',
                args: 'cat',
                ttyEnabled: true
                ),
             containerTemplate(
                name: 'helm',
                image: 'lachlanevenson/k8s-helm:v2.11.0',
                command: '/bin/sh -c',
                args: 'cat',
                ttyEnabled: true
                )
            ],
        volumes: [
            hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
            ]
    ){
          try{
            node(label){
                container('sbt'){
                    stage('Fetch Source Code'){
                        println("\u273F\u273F\u273F Begin to fetch source code \u273F\u273F\u273F")
                        fetchSourceCode(gitHubRepoInfo("source_code"), "source_code")
                        sh "ls -ltr src/ && pwd"
                    }

                    stage("Compile && Package && Code Check"){
                        dir("src/"){
                            sh "sbt compile && sbt dist"
                            sonarCheck(sonarInfo)
                            getSonarReport()
                        }
                    }
                }

                container('docker'){
                    stage("Docker Image Build"){
                            println("\u273F\u273F\u273F Begin to build docker images \u273F\u273F\u273F")
                            dockerImageBuild(dockerRepoAndImageInfo)  
                    }
                }
                container('helm'){
                    stage('Fetch Helm Files'){
                            fetchSourceCode(gitHubRepoInfo("helm_code"),"helm_code")
                            sh " ls -ltr && pwd"
                    }

                    stage('Helm Deploy'){
                            println(">>>>>>>>Begin to do helm action for Accesshub<<<<<<<")
                            dir('helm/helm_chart/accesshub/'){
                                doHelmAction4Accesshub()
                                sh "ls -ltr && pwd"
                            }
                    }
           }

}   
    
