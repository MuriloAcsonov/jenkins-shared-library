import com.pi.jenkins.utils.*

def call(Map pipelineParams){    

    def awsUtils = new awsUtils()
    def dockerUtils = new dockerUtils()
    def nodeUtils = new nodeUtils()
    def chatUtils = new chatUtils()

    pipeline{

        agent {
            kubernetes {
                cloud 'kubernetes'
                label 'lambda-deploy-slave'
                containerTemplate {
                    name 'lambda-deploy-slave'
                    image ''
                    ttyEnabled true
                    command 'cat'
                }
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
                        regionAWS = params.region_deploy
                        BUILD_USER_ID = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
                        print "Selected: " + regionAWS
                    }
                }
            }

            stage('Get AWS Credentials'){                                
                steps{
                    script{
                        awsCredMap = awsUtils.credentialAws(params.env_deploy, regionAWS, pipelineParams)                        

                        print "Selected Enviroment: " + params.env_deploy
                        print "Aws credential generated successfully"
                        print "Aws Account Id: " + awsCredMap["accountId"]
                        print "Aws Bucket: " + awsCredMap["bucketDeploy"]
                        print "Aws Lambda Function: " + awsCredMap["functionName"]
                    }
                }
            }

            stage('Test') { 
                when {
                    expression { pipelineParams["test"] == true || !pipelineParams.containsKey("test")}
                }                
                steps {
                        
                        script {
                                                        
                            def testCommand = 'npm test'
                            if(pipelineParams.containsKey('testCmd')){
                                testCommand = pipelineParams['testCmd']
                            }

                            nodeUtils.npmTest(testCommand)
                            
                        }

                }
            }
            stage('Deploy Serverless'){
                when {
                    expression { fileExists('serverless.yml') }
                }
                steps{                    

                    script{
                        
                        nodeUtils.serverlessInstall(testCommand)
                        awsUtils.serverlessDeploy(awsCredMap["credentialsAWS"], regionAWS)
                        
                        currentBuild.getRawBuild().getExecutor().interrupt(Result.SUCCESS)
                        sleep(2)
                    }

                }
            }
            stage ('Build') {
                steps {                
                    print 'We will build your application...'                    

                    withAWS(credentials: awsCredMap["credentialsAWS"]){
                        script{

                            pipelineParams['addOns'] = pipelineParams['addOns'] + ',' + 'zip'

                            dockerUtils.apkAdd(pipelineParams['addOns'])                            

                            def folder = 'src'

                            if(fileExists('tsconfig.json')) {

                                def jsonTs = readJSON file: "tsconfig.json"

                                folder = jsonTs.compilerOptions.outDir

                            }

                            nodeUtils.lambdaNodeBuild(fileExists('tsconfig.json'))

                            awsCredMap['packageId'] = dockerUtils.createPackage(pipelineParams['functionName'], folder)

                            awsUtils.cpS3Package(awsCredMap['packageId'], awsCredMap['bucketDeploy'])

                        }                        
                    }

                }             

            }
            stage('Deploy'){
                steps{
                    print 'Deploying...'
                    script {                                                        
                        
                        awsUtils.deployLambdaS3(regionAWS, awsCredMap)

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