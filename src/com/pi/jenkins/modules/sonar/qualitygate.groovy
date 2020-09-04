import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def get_options(opts){

  def options = [:]

  def option_names = [
    'check_timeout',
    'branch_config',
    'sonar_name'
  ]

  def option_defaults = [
    'check_timeout': 30,
    'branch_config': null,
    'sonar_name': env.SONAR_DEFAULT
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

def get_branch_options(branch) {
  default_options = [
    'fail': false,
    'mark_unstable': false
  ]

  if (options['branch_config'] == null) {
    return default_options
  }
  if (options['branch_config'][branch] != null) {
    return options['branch_config'][branch]
  }
  if (options['branch_config']['default'] != null) {
    return options['branch_config']['default']
  }

  return default_options
}

def notify_email(notify_options, sonar_url) {
  if (notify_options['destination'] != null) {
    emailext (
      subject: "[Sonar Check] Erro na validação de código-fonte no job ${env.JOB_NAME} #${env.BUILD_NUMBER}",
      body: "O código não passou na validação do Sonar. Para maiores detalhes, acesse ${sonar_url}",
      to: notify_options['destination']
    )
  }
  else {
    echo ("Não há destino configurado para a notificação via email.")
  }
}

def quality_check(check_options, project_name){

  def quality_gate = ['status':'UNKNOWN']

  try {
    withSonarQubeEnv(options['sonar_name']) {
      timeout(time: options['check_timeout'], unit: 'MINUTES') {
        quality_gate = waitForQualityGate()
      }
    }
  }
  catch (FlowInterruptedException e) {
    echo ("Timeout atingido aguardando análise do Sonar.")
    return 'FAILURE'
  }
  catch (Exception e) {
    echo ("Não foi possível checar o status da análise do Sonar: ${e}")
    return 'UNSTABLE'
  }

  if (quality_gate.status != 'OK') {

    if ( check_options.notification != null) {
      def i
      for (i=0; i < check_options.notification.size(); i++) {

        type = check_options.notification[i]['type']
        options = check_options.notification[i]['options']

        if (type == null) {
          echo ("Notificação sem tipo definido. Ignorando.")
          continue
        }

        project_name = (project_name != null) ? project_name : 'Unknown'

        switch(type) {
          case 'email':
            notify_email(options, "${env.SONAR_URL}/dashboard/index?id=${project_name}%3A${env.BRANCH_NAME}")
            break
          default:
            echo ("Notificação de tipo não suportado. (type=${type})")
            break
        }
      }
    }

    echo ("Código não passou na validação do Sonar (status = ${quality_gate.status})")
    if (check_options['fail']) {
      return 'FAILURE'
    }
    if (check_options['mark_unstable']) {
      return 'UNSTABLE'
    }
    return 'SUCCESS'
  }
  echo ("Código passou com sucesso na validação do Sonar (status = ${quality_gate.status})")
  return 'SUCCESS'
}

def destruct(module_opts, context, results) {
  return true
}

def call(module_opts, context) {
  def result = [ output:[:],
                 stats:[:],
                 status:''
               ]

  options = get_options(module_opts)
  branch_options = get_branch_options(env.BRANCH_NAME)

  result.status = quality_check(branch_options, context.environment['repo_name'])
  return result
}

return this;
