package com.pi.jenkins.utils

def npmInstall(){
    sh (script: "npm install")
}

def npmTest(testCmd){

    print 'Start Tests'
    npmInstall()
    sh (script: testCmd)

}

def serverlessInstall(){
    print 'We will deploy the serverless yml...'

    sh (script: "npm install -g serverless")
    npmInstall()    

}

def lambdaNodeBuild(tsFile){
    sh (script: "rm -rf package-lock.json node_modules")
    npmInstall()    

    if(tsFile){
        print "Hmmm, typescript project..."
        sh (script: "npm i -g typescript")
        sh (script: "tsc -p .")
    }
    
}

def cfDeploy(envAWS, install){
    print "Deploying react in ${envAWS}"

    if(install){
        npmInstall()
    }

    sh (script: "npm run client:build:${envAWS}")
}

def newRelicApm(tsFile, envAWS){
    def file = null

    def jsonPackage = readJSON file: "package.json"

    if(tsFile && fileExists("src/main.ts")){        
        file = "src/main.ts"
    }
    else if(jsonPackage.containsKey("main")){
        file = jsonPackage.main
    }
    else{
        print "Please, consider refer main file in package.json, using 'main':'file'"
        currentBuild.getRawBuild().getExecutor().interrupt(Result.FAILURE)
        sleep(2) 
    }
    
    nrEditFile(file, envAWS, tsFile ? 1 : 0)

}

def nrEditFile(file, envAWS, statement){

    def nrStatement = ["require('newrelic')", "import * as newrelic from 'newrelic'"]

    def fileReader = readFile file: file    
    def lines = fileReader.readLines()
    indexes = lines.findIndexValues{ it =~ /.*newrelic.*/ }

    if(indexes.size() > 0){
        switch(envAWS) {
            case 'dev':
            case 'hml':
                
                count = 0
    
                for(index in indexes){
                    index -= count
                    lines.remove(index.toInteger())    
                    count++
                }

            break

        }

    }
    else if(envAWS == 'prd'){
        lines = lines.plus(0, nrStatement[statement])
    }

    fileReader = lines.join('\n')    

}

return this