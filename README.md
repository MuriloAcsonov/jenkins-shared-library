# Pipelines

## Biblioteca compartilhada Jenkins para automatizar a criação de steps repetitivos. (future: Modulos DevSecOps)

### Descrição
-----
Este repositório contém uma **Jenkins Shared library**:

- Funções comuns entre pipelines [/src/com/pi/jenkins/](/src/com/pi/jenkins)
- Pipelines padrão por linguagem [/vars/](/vars/)
- Steps/Ferramentas DSL comuns [/modules/](/modules/)
- Métodos DSL compartilhados [/vars/](/vars/)

### Objetivo
-----
Centralizar codificação futura das pipelines utilizando [DSL](https://jenkins.io/doc/book/pipeline/shared-libraries/#defining-a-more-structured-dsl), garantindo a filosofia [DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself).


### Instalação
-----
1. Logado no Jenkins com credenciais de Administração, vá a *Manage Jenkins -> Configure System -> Global Pipeline Libraries*

2. Preencha:

  - **Name**: Nome único (ex: pipelines)
  - **Version**: Branch ou Tag (ex: master)
  
  - [x] **Carrega implicitamente**
  - [x] **Permite override de versão padrão**
  - [x] **Inclui alterações de @Library nas modificações da job (Change log)**

  - **Modo de obtenção**
    - [x] **Modern SCM**
  - **Source Code Management**
    - [x] **Git**


### Override da versão padrão
-----

Cole na primeira linha do Jenkisfile:
*Jenkinsfile*
```java
@Library('libraryName@libraryVersion') _
```

| **Parâmetro**             | **Descrição**                                                     |
|-------------------------  |------------------------------------------------------------------ |
| **`libraryName`**         | Nome único Jenkins Shared Libray                                  |
| **`libraryVersion`**      | Relacionado às ações de SCM (commit, branch, tag)                 |

## Como Utilizar a primeira versão disponível da Shared Library:
-------
> Disponível ainda somente para deploy de microserviços ecs node

```groovy
@Library('jenkins-library@develop')_

nodePipeline([  taskName: 'name-of-task', 
                repoName : 'repo-name', 
                test : true or false (only - default true), 
                testCmd : 'test command',
                stopTasks : true or false (only - default false),
                clusterName : 'name-ofCluster (without sufix of environment - hml, prd or dev)',
                 containerVars : """[
                                {
                                  'name' : 'TEST',
                                  'value': 'env_var_test'
                                }
                              ]"""

            ])
```
### Explicando os parametros:

* **taskName** - Nome da sua task. ps: sem letras maiusculas. (variavel obrigatória)

* **repoName** - Nome do path da imagem do seu projeto. (variavel opcional, padrão: *taskName*)
 > ex: você irá passar somente a parte destacada em maiusculo: <strike>0000.dkr.ecr.us-east-1.amazonaws.com/</strike>**REPO-NAME**

 * **test** - Boolean (*true* ou *false*) para passar ou não pelo stage de teste. (variavel opcional, padrão: *true*)

 * **testCmd** - Comando executado no stage de teste. (variavel opcional, padrão: *npm test*)

 * **stopTasks** - Boolean (*true* ou *false*) para matar as tasks já existentes no cluster escolhido. (variavel opcional, padrão: *false*)

 * **clusterName** - Nome do cluster escolhido para sua task, não passar o sufixo de ambiente, exemplo: dev, hml ou prod. (variavel obrigatória)

> ex: você irá passar todo o nome sem o sufixo final: **nome-EcsCluster**<strike>-hml</strike>

 * **containerVars** - Variaveis de ambiente de dentro do container. (variavel opcional) 

> ex: você irá passar um json como mostrado no bloco de código de exemplo acima.
> Variaveis disponiveis para se usar como valor no json - **%regionAws%** (para usar a região escolhida), **%environment%** (para usar o ambiente escolhido) - sempre coloque entre percents e escreva exatamente como está.