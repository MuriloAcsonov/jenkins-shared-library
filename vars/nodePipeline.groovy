import com.pi.jenkins.utils.*

def call(Map pipelineParams){    

    def awsUtils = new awsUtils()
    def k8sUtils = new k8sUtils()
    def dockerUtils = new dockerUtils()
    def nodeUtils = new nodeUtils()
    def chatUtils = new chatUtils()

    pipeline{

        agent {
            kubernetes {
                label 'docker-slave-' + pipelineParams["taskName"]
                defaultContainer 'jnlp'
                yaml k8sUtils.yamlNodeFile(pipelineParams["taskName"])
            }
        }

         parameters{

            choice(

                name:"env_deploy",
                choices: ["dev", "hml", "prd"],
                description: "Which environment to deploy?"                  

            )
            choice(

                name:"region_deploy",
                choices: ["us-east-1", "sa-east-1"],
                description: "Which region to deploy?"
                
            )

        }               

        stages {            

            stage('Set AWS Region'){                

                environment{                    
                    AWS_REGION = awsUtils.regionAws(params.region_deploy)                    
                }
                steps{
                    script{
                        BUILD_USER_ID = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
                        regionAWS = params.region_deploy
                        print "Selected: " + regionAWS
                    }
                }
            }

            stage('Get AWS Credentials'){                                
                steps{
                    script{
                        pipelineParams['repoName'] = pipelineParams.containsKey("repoName") ? pipelineParams["repoName"] : pipelineParams["taskName"]                        

                        awsCredMap = awsUtils.credentialAws(params.env_deploy, regionAWS, pipelineParams)
                        
                        pipelineParams["clusterName"] = awsCredMap["clusterName"]

                        environmentAws = params.env_deploy

                        print "Selected Enviroment: ${environmentAws}"
                        print "Selected branch: ${env.BRANCH_NAME}"
                        print "Aws credential generated successfully"
                        print "Aws Account Id: " + awsCredMap["accountId"]
                        print "Aws Repository: " + awsCredMap["ecrRepo"]                             
                        
                    }
                }
            }

            stage('Test') { 
                when {
                    expression { pipelineParams["test"] == true || !pipelineParams.containsKey("test")}
                }                
                steps {
                    container('docker-slave-' + pipelineParams["taskName"]) {
                        
                        script {
                                                        
                            def testCommand = 'npm test'
                            if(pipelineParams.containsKey('testCmd')){
                                testCommand = pipelineParams['testCmd']
                            }

                            nodeUtils.npmTest(testCommand)
                            
                        }

                    }

                }
            }
            stage('New Relic Manager') {
                steps{
                    print 'Only prod deploy goes to new relic apm!'
                    container('docker-slave-' + pipelineParams["taskName"]) {

                        script{
                            
                            nodeUtils.newRelicApm(fileExists('tsconfig.json'), environmentAws)

                        }

                    }
                }
            }
            stage ('Docker Build') {
                steps {                
                    print 'We will build your application...'
                    container('docker-slave-' + pipelineParams["taskName"]) {

                        script {                                                        

                            gitVersion = sh (script: "git show --format='%h' --no-patch", returnStdout: true).trim()

                            switch(pipelineParams['imageTag'].toString().toUpperCase().trim()){

                                case "GIT":

                                    dockerTag = gitVersion

                                break;

                                case "BUILD_GIT":

                                    dockerTag = String.format("%s.%s", gitVersion, BUILD_NUMBER)

                                break;
                                
                                default:

                                    dockerTag = BUILD_NUMBER

                                break;

                            }

                            dockerUtils.buildImage(awsCredMap["ecrRepo"], dockerTag)
                            print 'Lets push...'
                            dockerUtils.pushImage(awsCredMap["credentialsAWS"], regionAWS, awsCredMap["ecrRepo"], dockerTag)

                        }                        

                    }

                }             

            }
            stage('Environment Manager'){
                when {
                    expression { pipelineParams.containsKey("secretsManagerId") || pipelineParams.containsKey("containerVars") }
                }
                steps{

                    container('docker-slave-' + pipelineParams["taskName"]) {

                        script{

                            print "Getting secrets..."
                            
                            pipelineParams['credentialsAws'] = awsCredMap["credentialsAWS"]

                            pipelineParams["containerVars"] = awsUtils.getSecrets(pipelineParams)

                        }                   

                    }

                }
            }
            stage('Deploy'){
                steps{
                    print 'Deploying...'                    
                    container('docker-slave-' + pipelineParams["taskName"]) {

                        script {

                            def paramsMap = [:]

                            paramsMap['ecrImage'] = awsCredMap["ecrRepo"] + ':' + dockerTag
                            paramsMap['credentialsAws'] = awsCredMap["credentialsAWS"]
                            paramsMap['taskName'] = pipelineParams["taskName"]
                            paramsMap['regionAws'] = regionAWS
                            paramsMap['environmentAws']= environmentAws
                            paramsMap['clusterName'] = pipelineParams["clusterName"]
                            paramsMap['serviceName'] = pipelineParams.containsKey("serviceName") ? pipelineParams['serviceName'] : pipelineParams["taskName"]
                            paramsMap['containerVars'] = pipelineParams["containerVars"]
                            paramsMap['secretsManagerId'] = pipelineParams["secretsManagerId"]                            

                            awsUtils.newTaskDefinition(paramsMap, pipelineParams["stopTasks"])

                        }                        

                    }

                }
            }
        }

        post{
            always{
                script{                    
                    chatUtils.defaultNotifyAction(currentBuild.result, env.BRANCH_NAME, JOB_NAME, BUILD_NUMBER, BUILD_USER_ID, environmentAws, regionAWS)
                }                
            }
        }

    }    

}