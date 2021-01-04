#!/usr/bin/env groovy
def call(body) {
def pluginDependencies = ['docker-workflow']
for (int i =0; i < pluginDependencies.size(); i++) {
   if (Jenkins.getInstance().getPluginManager().getPlugin(pluginDependencies[i]) == null) {
	      error "This shared library function requires '${pluginDependencies[i]}'!"
	    }
	}
   def GlobalTimeout = 60 // when should a pipeline abort (minutes)
   def ProjectName = null // name of the project (as in branch or tag name)
   def TargetName = null;
   def FLASHPORT =  '/dev/ttyUSB0'; //should be argument of pipeline or node var
   //for example PackerArchVersion='arduino:samd@1.6.9'
   def PackerArchVersion = 'esp8266:esp8266'

   // retrieving 'configuration' from closure content (params of this method)
   def config = [:]
   body.resolveStrategy = Closure.DELEGATE_FIRST
   body.delegate = config
   body()

   ProjectName = config.ProjectName
   if ( null == ProjectName || "" == ProjectName ) {
      error "Project needs at least a projectName property with some meaningful value"
   }
   fqbn = config.fqbn
   if ( null == fqbn || "" == fqbn ) {
      error "Project needs at least a fqbn property with some meaningful value like esp8266:esp8266:d1_mini_pro"
   }
   PackerArchVersion = config.PackerArchVersion
   if ( null == PackerArchVersion || "" == PackerArchVersion ) {
      error "PackerArchVersion should be something like  'arduino:samd@1.6.9'"
   }

   if ( null != config.AgentLables ) {
      echo "Using supplied agentLables '${config.AgentLables}'"
      AgentLabel = "${config.AgentLables}"
   }

   pipeline {
      agent none
      options {
         preserveStashes(buildCount: 3)
         buildDiscarder(logRotator(numToKeepStr: '3'))
	      timestamps()
	      disableConcurrentBuilds()
	      timeout(time: GlobalTimeout, unit: 'MINUTES')
	   }
	   triggers {
	      pollSCM('H/5 * * * *')
	      // master branches may build regularly to get updates
	      cron(env.BRANCH_NAME ==~ /.*-master/ ? '@daily' : '')
	   }
	   environment {
	      MYENV = 'undef'
	   }
      stages {
         //agent { any }
	      stage('test') {
	         steps {
               echo 'Testing..'
	         }
	      }
	      stage('build'){
            agent { label 'arduinocli' }
	         steps {
               echo 'Building..'
               sh label: 'arduino link board', returnStatus: true, script: 'ln -s ~/Arduino'
               sh label: 'arduino link libs', returnStatus: true, script: 'ln -s ~/.arduino15'
               sh label: 'arduino build', returnStatus: true, script: "/usr/local/arduino-cli/arduino-cli -v compile --build-path ${WORKSPACE}/target/ --fqbn \"${fqbn}\" ${ProjectName}"
               stash includes: '**/*.bin', name: 'arduino'
	         }
	      }
         stage('Flash') {
            agent { label 'ARM' }
            steps {
               echo 'Flash....'
               unstash 'arduino'
               sh label: 'arduino core install', returnStatus: true, script: "/usr/local/arduino-cli/arduino-cli core install ${PackerArchVersion}"
               sh label: 'arduino upload', returnStatus: false, script: "/usr/local/arduino-cli/arduino-cli -v upload -i target/${ProjectName}.ino.bin --fqbn \"${fqbn}\" -p ${FLASHPORT}"
            }
         }

	   } //END STAGES
	   post {
	      always {
	         // delete moved to stage deploy
	         echo "Pipeline run finished"
	      }
	   }
   } //Pipeline
} //body

