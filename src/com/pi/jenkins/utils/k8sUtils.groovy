package com.pi.jenkins.utils

def yamlNodeFile(taskName){

    def yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                build: ${taskName}
            spec:
              containers:
              - name: docker-slave-${taskName}
                image: 
                securityContext:
                  privileged: true
                resources:
                  limits:
                    memory: "2Gi"
                    cpu: "2"
                  requests:
                    memory: "1Gi"
                    cpu: 1
                tty: true
                volumeMounts:
                - name: dockersock
                  mountPath: /var/run/docker.sock
                - name: dockerconfig
                  mountPath: /var/lib/docker/
                command:
                - cat
              volumes:
              - name: dockersock
                hostPath:
                  path: /var/run/docker.sock
              - name: dockerconfig
                hostPath:
                  path: /var/lib/docker/
            """

    return yaml

}

def yamlCFFile(taskName){

  def yaml = """
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                some-label: ${taskName}
            spec:
              containers:
              - name: docker-slave-${taskName}
                image: 
                securityContext:
                  privileged: true
                resources:
                  limits:
                    memory: "2Gi"
                    cpu: "2"
                  requests:
                    memory: "1Gi"
                    cpu: 1
                tty: true
                volumeMounts:
                - name: dockersock
                  mountPath: /var/run/docker.sock
                - name: dockerconfig
                  mountPath: /var/lib/docker/
                command:
                - cat
              volumes:
              - name: dockersock
                hostPath:
                  path: /var/run/docker.sock
              - name: dockerconfig
                hostPath:
                  path: /var/lib/docker/
            """

  return yaml

}

return this

