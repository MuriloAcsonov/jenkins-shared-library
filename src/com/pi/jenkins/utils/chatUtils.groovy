package com.pi.jenkins.utils

def defaultNotifyAction(result, branch, job, number, executor, env, region){  

    if(result == 'FAILURE'){
        
    def tokenId = ""

    job = job.split('/')[0]

    def messageError = "The job: ${job} has *FAILED*!\n\nTarget Environment: ${env}\nTarget Region: ${region}\nBranch: ${branch}\nStarted by: ${executor}\n\nPlease, consult the logs of build number: ${number} for more information..."

    hangoutsNotify(message: messageError, token: tokenId, threadByJob: false)

    }

}

return this