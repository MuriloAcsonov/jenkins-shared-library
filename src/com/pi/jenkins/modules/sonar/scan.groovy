def get_options(opts){

  def options = [:]

  def option_names = [
    'install_scanner',
    'sonar_name',
    'project_name',
    'project_key',
    'sources',
    'basedir',
    'exclude',
    'additional_properties',
    'include_branch',
    'platform'
  ]

  def option_defaults = [
    'install_scanner': true,
    'sonar_name': env.SONAR_DEFAULT,
    'project_name': null,
    'project_key': null,
    'sources': './',
    'basedir': '.',
    'exclude': '',
    'additional_properties': [],
    'include_branch': false,
    'platform': 'unix'
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

def scan() {

  withSonarQubeEnv(options['sonar_name']) {

    def scanner_path = ""
    def project_key

    if (options['install_scanner']) {
      echo "Instalando Sonar Scanner ..."
      tool_home = tool(env.SONAR_TOOL)
      scanner_path = "${tool_home}/bin/"
    }

    scanner_path = "${scanner_path}sonar-scanner"

    if (options['project_key'] != null) {
      project_key = options['project_key']
    }
    else {
      project_key = "${options['project_name']}:${env.BRANCH_NAME}"
    }

    //review SonarQube CE allowed parameters
    scan_command = "${scanner_path} \
      -Dsonar.projectKey=${project_key} \
      -Dsonar.projectName=${project_key} \
      -Dsonar.login=${env.SONAR_AUTH_TOKEN} \
      -Dsonar.host.url=${env.SONAR_HOST_URL} \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.sources=${options['sources']} \
      -Dsonar.projectBaseDir=${options['basedir']} \
      -Dsonar.exclusions=${options['exclude']}"

    if(options.platform=='unix') {
      scan_command = "${scan_command} -Dsonar.projectVersion=`git rev-parse --short HEAD`"
    }

    if (options['include_branch']) {
      scan_command = "${scan_command} -Dsonar.branch.name=${env.BRANCH_NAME}"
    }

    options['additional_properties'].each {
      scan_command = "${scan_command} -D${it}"
    }

    try {
      switch(options.platform) {
        case 'unix':
          sh(scan_command)
          break
        case 'windows':
          bat(scan_command)
          break
        default:
          echo ("Plataforma não reconhecida - ${options.platform}.")
          return "FAILURE"
      }
      return 'SUCCESS'
    }
    catch(Exception e) {
      echo ("Erro ao rodar Sonar Scan: ${e}")
      return 'UNSTABLE'
    }
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
  result.output['project_name'] = options['project_name']

  result.status = scan()
  return result
}

return this;
