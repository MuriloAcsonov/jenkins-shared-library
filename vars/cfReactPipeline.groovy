import com.pi.jenkins.utils.*

def call(Map pipelineParams){    

    def awsUtils = new awsUtils()
    def k8sUtils = new k8sUtils()
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
                choices: ["dev", "hml", "prod"],
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

                        awsCredMap = awsUtils.credentialAws(params.env_deploy, regionAWS, pipelineParams)                      

                        environmentAws = params.env_deploy

                        print "Selected Enviroment: ${environmentAws}"
                        print "Aws credential generated successfully"
                        print "Aws Account Id: " + awsCredMap["accountId"]
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

            stage('Deploy React') {                 
                steps {                   
                        
                    script {                            

                        nodeUtils.cfDeploy(environmentAws, pipelineParams["test"] == false)

                    }

                }
            }

            stage ('Publish CF in S3') {
                steps {                
                    print 'Pushing...'                   

                    script {                            
                            
                        pipelineParams["credentialsAWS"] = awsCredMap["credentialsAWS"]
                        pipelineParams["bucketName"] = pipelineParams["bucketName"].replace('%env%', environmentAws).replace(' ', '')

                        cloudfrontId = pipelineParams["cloudfrontIds"]
                        pipelineParams["cloudfrontId"] = cloudfrontId[environmentAws]                        

                        awsUtils.pushCFS3(pipelineParams)

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