def get_options(opts){

  def options = [:]

  def option_names = [
    'archive',
    'type',
    'branches',
    'destination',
    'notification',
    'fail',
    'reportName',
    'reportFile',
    'reportDir'
  ]

  def option_defaults = [
    'archive': null,
    'type': null,
    'branches': ['dev','hml', 'master'],
    'destination': null,
    'notification': null,
    'fail': false,
    'reportName': 'Unit Test',
    'reportFile': 'index.html',
    'reportDir': 'reports'

  ]

  // Getting module options and setting defaults
  def i=0
  for (i=0; i < option_names.size(); i++) {
    name = option_names[i]
    value = (opts!=null && opts[name] != null) ? opts[name] : option_defaults[name]
    options.put (name, value)
  }

  return options
}

def get_command(cmd_map, branch) {
  if (cmd_map == null) {
    return null
  }
  if (cmd_map[branch] != null) {
    return cmd_map[branch]
  }
  if (cmd_map['default'] != null) {
    return cmd_map['default']
  }
  return null
}

def notify_email(destination) {
  if (destination != null) {
    emailext (
      subject: "[Teste] Finalizado para ${env.JOB_NAME} #${env.BUILD_NUMBER}",
      body: "Teste finalizado. Mais detalhes: ${env.BUILD_URL}",
      to: destination
    )
  }
  else {
    echo ("Não há destino configurado para a notificação via email.")
  }
}

def destruct(module_opts, context, results) {
  return true
}

def call(module_opts, context) {
  def result = [ output:[:],
                 stats:[:],
                 status:'SUCCESS'
               ]

  options = get_options(module_opts)

  if (! options['branches'].contains(env.BRANCH_NAME)) {
    result.status = 'SUCCESS'
    return result
  }

  echo ("Inicializando a criação do relatorio")
  try {
        script {
          if (options['type'] == 'cucumber'){
            cucumber fileIncludePattern: 'target/**.json', sortingMethod: 'ALPHABETICAL'
          }
          else if (options['type'] == 'html'){
            publishHTML (target: [ allowMissing: false,  alwaysLinkToLastBuild: false, keepAll: true, reportDir: options['reportDir'], reportFiles: options['reportFile'], reportName: options['reportName'] ])
          }
          if (options['archive'] != null){
              try {
                archiveArtifacts options['archive']
              } catch(Exception ex) {
                echo ("Artefatos não encontrados. Ignorando.")
              }
          }
          if (options['destination'] != null) {
              notify_email(options['destination'])
          }
        }


  } catch(Exception ex) {
    echo ("Ocorreu um erro ao criar o relatorio")
    echo (ex.getMessage())
    echo (ex.toString())
    if (options['fail']){
	    result.status = 'FAILURE'
      return result
    }
  }


  return result
}

return this

