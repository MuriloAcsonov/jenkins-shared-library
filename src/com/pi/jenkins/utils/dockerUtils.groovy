package com.pi.jenkins.utils

def buildImage(repositorioEcr, dockerTag){

    sh (script: "docker build -t ${repositorioEcr}:${dockerTag} . --network host")

}

def pushImage(credentialAws, regionAws, repositorioEcr, dockerTag){

    withAWS(credentials: credentialAws){

        sh (script: "aws --version")        
        sh (script: "aws ecr get-login-password --region ${regionAws}|docker login --username AWS --password-stdin ${repositorioEcr}")
        sh (script: "docker push ${repositorioEcr}:${dockerTag}")

    }

}

def apkAdd(addOns){    
    sh (script: "apt-get update")    

    for(addOn in addOns.split(',')){
        try{            
            sh (script: "apt-get install -y ${addOn}")
        }
        catch(Exception ex) {
         print "Exception: " + ex
      }
    }    
}

def createPackage(functionName, folder){

    def packageId = ''

    try {        
    packageId = sh (script: "echo ${functionName} | md5sum | cut -b 1-32", returnStdout: true).trim()
    packageId = packageId + '.zip'

    sh (script: "zip -9 -r ${packageId} ${folder} node_modules", returnStdout: true)

    }
    catch(Exception ex){
        print "Exception: " + ex
    }

    return packageId
}

return this