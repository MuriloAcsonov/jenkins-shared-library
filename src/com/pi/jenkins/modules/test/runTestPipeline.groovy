def get_options(opts){

  def options = [:]

  def option_names = [
    'pipeline_per_branch',
    'enabled',
    'branches',
    'fail'
  ]

  def option_defaults = [
    'pipeline_per_branch': null,
    'enabled': true,
    'branches': ['dev','hml', 'master'],
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

  if (get_command(options['pipeline_per_branch'], env.BRANCH_NAME) == null){
      echo ("Pipeline não foi definido corretamente.")
      result.status = 'FAILURE'
      return result
  }

  echo ("Inicializando a pipeline de teste.")
  try {

  	def jobBuild = build job: get_command(options['pipeline_per_branch'], env.BRANCH_NAME)
  	def jobResult = jobBuild.getResult()

  	echo ("Build of ${jobBuild} returned result: ${jobResult}")

  	buildResults['testJob'] = jobResult

  	if (jobResult != 'SUCCESS' && options['fail']) {
    		result.status = 'FAILURE'
    		return result
  	}

  } catch(Exception ex) {
    println("Ocorreu uma exception durante a execução do pipeline de teste.")
    if (options['fail']){
	    result.status = 'FAILURE'
      return result
    }
  }


  return result
}

return this

