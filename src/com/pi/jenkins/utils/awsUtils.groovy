package com.pi.jenkins.utils
import groovy.json.*

def regionAws(region){
    return region
}

def credentialAws(environment, regionAws = 'us-east-1', mapConfig){

    def awsMap = [:]    

    mapConfig['regionAws'] = regionAws
    mapConfig['env'] = environment

    switch(environment) {

        case 'dev':

        awsMap['credentialsAWS'] = ''
        awsMap['accountId'] = ''        

        break;

        case 'hml':

        awsMap['credentialsAWS'] = ''
        awsMap['accountId'] = ''
        
        break;

        case 'prd':

        awsMap['credentialsAWS'] = ''
        awsMap['accountId'] = ''        

        break;
    }
    
    awsMap = configMap(mapConfig, awsMap)
    return awsMap

}

def configMap(Map mapConfig, Map awsMap){

    if(mapConfig.containsKey('repoName')){

        awsMap['ecrRepo'] = awsMap['accountId'] + ".dkr.ecr." + mapConfig['regionAws'] + ".amazonaws.com/" + mapConfig['repoName']

        def clusterName = mapConfig['clusterName']

        if(mapConfig['hasEnvSuffix']){

             clusterName = clusterName + '-' + mapConfig['env']

        }

        awsMap['clusterName'] = clusterName

    }
    else if(mapConfig.containsKey('functionName')){

        def functionName = mapConfig['functionName']

        if(mapConfig['hasEnvSuffix']){

            functionName = functionName + '-' + mapConfig['env']

        }

        awsMap['functionName'] = functionName
        awsMap['bucketDeploy'] = mapConfig.containsKey('bucketDeploy') ? awsMap['bucketDeploy'] : 'default-lambda-packages-deploy' + '-' + mapConfig['env'] + '-' + mapConfig['regionAws'].split('-')[0]

    }

    return awsMap

}

def newTaskDefinition(paramsMap, stopTask = false) {

    withAWS(credentials: paramsMap['credentialsAws']){
        
        def oldTdJson = sh(script: "aws ecs describe-task-definition --task-definition " + paramsMap['taskName'] + " --region " + paramsMap['regionAws'], returnStdout: true)
                
        def jsonFormated = jsonReader(oldTdJson)
        def family = jsonFormated.taskDefinition.family

        jsonFormated.taskDefinition.remove('taskDefinitionArn')
        jsonFormated.taskDefinition.remove('revision')
        jsonFormated.taskDefinition.remove('status')
        jsonFormated.taskDefinition.remove('requiresAttributes')
        jsonFormated.taskDefinition.remove('compatibilities')

        jsonFormated.taskDefinition.containerDefinitions[0].image = paramsMap['ecrImage']

        paramsMap['containerVars'] = paramsMap['containerVars'].replace("%regionAws%", paramsMap['regionAws'])
        paramsMap['containerVars'] = paramsMap['containerVars'].replace("%environment%", paramsMap['environmentAws'])

        jsonFormated.taskDefinition.containerDefinitions[0].environment = paramsMap['containerVars'].replace("\"", "'")
        
        def newTdJson = jsonBuilder(jsonFormated.taskDefinition).replace("\"", "\\\"").replace("'", "\\\"").replace("\\\"[", "[").replace("]\\\"", "]")

        print newTdJson

        def newTdInfo = sh(script: "aws ecs register-task-definition --region " + paramsMap['regionAws'] + " --cli-input-json ${newTdJson}", returnStdout: true)
        jsonFormated = jsonReader(newTdInfo)
        def newRevision = jsonFormated.taskDefinition.revision        

        if(stopTask){            

            def tasksJson = sh(script: "aws ecs list-tasks --region " + paramsMap['regionAws'] + " --cluster " + paramsMap['clusterName'] + " --family ${family}", returnStdout: true)
            jsonFormated = jsonReader(tasksJson)

            for(taskArn in jsonFormated.taskArns){
                print "Stopping task: ${taskArn}"
                sh(script: "aws ecs stop-task --region " + paramsMap['regionAws'] + " --cluster " + paramsMap['clusterName'] + " --task ${taskArn}")
            }
            
        }

        sh(script: "aws ecs update-service --region "+ paramsMap['regionAws'] +" --cluster "+ paramsMap['clusterName'] +" --service "+ paramsMap['serviceName'] +" --task-definition "+ paramsMap['taskName'] +":${newRevision} --force-new-deployment")

    }

}

@NonCPS
def jsonReader(jsonText){
    return new JsonSlurperClassic().parseText(jsonText)
}

def jsonBuilder(jsonText){
    return new JsonBuilder(jsonText).toString()
}

def cpS3Package(packageId, bucketDeploy){
    sh (script: "aws s3 cp ./${packageId} s3://${bucketDeploy}/${packageId}")
}

def deployLambdaS3(regionAws, awsCredMap){
    withAWS(credentials: awsCredMap["credentialsAWS"]){
        sh (script: "aws lambda update-function-code --region ${regionAws} --function-name " + awsCredMap['functionName'] + " --s3-bucket " + awsCredMap['bucketDeploy'] + " --s3-key " + awsCredMap['packageId'])
    }
}

def pushCFS3(paramsMap){
    withAWS(credentials: paramsMap["credentialsAWS"]){

        sh (script: "aws s3 rm s3://" + paramsMap["bucketName"] + " --recursive")
        sh (script: "aws s3 sync " + paramsMap["folderBuild"] + " s3://" + paramsMap["bucketName"])
        sh (script: "aws cloudfront create-invalidation --distribution-id " + paramsMap["cloudfrontId"] + " --paths '/*'")

    }
}

def serverlessDeploy(credentialsAWS, regionAWS){
    withAWS(credentials: credentialsAWS){
        sh (script: "serverless deploy --region ${regionAWS}")
    }
}

def getSecrets(paramsMap){

    def listEnvs = []    

    if(paramsMap.containsKey('secretsManagerId')){
        
        withAWS(credentials: paramsMap['credentialsAws']){

            def secrets = sh(script: "aws secretsmanager get-secret-value --secret-id " + paramsMap['secretsManagerId'] + " --region " + paramsMap['regionAws'], returnStdout: true)

            def jsonFormated = jsonReader(secrets)
            jsonFormated = (Map) jsonReader(jsonFormated.SecretString)        

            jsonFormated.each{ key, value ->

                def mapEnv = [:]

                mapEnv["name"] = key
                mapEnv["value"] = value

                listEnvs.add(mapEnv)

             }            
        }

    } 

    if(paramsMap.containsKey('containerVars')){

        def jsonFormated = jsonReader(paramsMap['containerVars'].replace("\n", "").replace(" ",""))

        for(json in jsonFormated){
            listEnvs.add(json)
        }        

    }

    return jsonBuilder(listEnvs)

}

return this
