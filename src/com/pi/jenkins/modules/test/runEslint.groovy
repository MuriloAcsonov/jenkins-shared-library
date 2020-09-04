def get_options(opts){

  def options = [:]

  def option_names = [
    'pwd',
    'command',
    'command_per_branch',
    'credentials',
    'fail'
  ]

  def option_defaults = [
    'pwd': null,
    'command': null,
    'command_per_branch': null,
    'credentials': null,
    'fail': false
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

def interpolate_value(value, map) {
  def string = value
  map.each {
    string = string.replaceAll(it.key, it.value)
  }
  return string
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


def exec(command, pwd) {
  def cmd="";
  if (pwd != null) {
    cmd = "cd ${pwd};"
  }
  cmd = "${cmd} ${command}"

  def return_code
  if (options.credentials != null) {
    if (options.credentials.type == 'ssh') {
      sshagent(credentials: [options.credentials.name]) {
        return_code = sh(script: cmd, returnStatus: true)
      }
    }
    if (options.credentials.type == 'aws') {
      withAWS(credentials: options.credentials.name) {
        return_code = sh (script: cmd, returnStatus: true)
      }
    }
    if (options.credentials.type == 'userpass') {
      withCredentials([
        usernamePassword(credentialsId: options.credentials.name,
                         passwordVariable: 'PASSWORD',
                         usernameVariable: 'USERNAME')
                      ])
      {
        return_code = sh (script: cmd, returnStatus: true)
      }
    }
    if (options.credentials.type == 'aws-env') {
      withCredentials([[
        $class: 'AmazonWebServicesCredentialsBinding',
        credentialsId: options.credentials.name,
        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
      ]]) {
        return_code = sh (script: cmd, returnStatus: true)
      }
    }
    if (options.credentials.type == 'ssh-npm') {
      withCredentials([
        sshUserPrivateKey(
          credentialsId: options.credentials.name,
          keyFileVariable: 'SSH_KEY')
        ])
      {
        withEnv(["GIT_SSH_COMMAND=ssh -i ${env.SSH_KEY} -o StrictHostKeyChecking=no"]) {
          return_code = sh (script: cmd, returnStatus: true)
        }
      }
    }
  }
  else {
    return_code = sh (script: cmd, returnStatus: true)
  }
  return return_code
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

  if (options.credentials != null) {
    if (options.credentials.type == null || options.credentials.name == null) {
      echo ("Credencial mal configurada. Deve possuir atributos 'name' e 'type'.")
      result.status = 'FAILURE'
      return result
    }
  }

  command =  (options['command']!=null) ? options['command'] : get_command(options['command_per_branch'], env.BRANCH_NAME)
  
  def map = ['%BRANCH_NAME%' : env.BRANCH_NAME, '%BUILD_NUMBER%' : env.BUILD_NUMBER, '%JOB_BASE_NAME%': env.JOB_BASE_NAME]
  def command = command != null ? interpolate_value(command, map) : interpolate_value(command, map)
  
  if (command == null) {
    echo ("Comando não especificado ou não definido para branch. Veja as opções 'command' e 'command_per_branch'")
    result.status = 'FAILURE'
  }

  def return_code = exec(command, options['pwd'])
  if (return_code != 0 && options['fail']) {
    result.status = 'FAILURE'
  }

  return result
}

return this
