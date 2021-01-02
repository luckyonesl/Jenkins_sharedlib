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

	// retrieving 'configuration' from closure content (params of this method)
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
   ProjectName = config.ProjectName
   if ( null == projectName || "" == projectName ) {
	   error "Project needs at least a projectName property with some meaningful value"
	}
	if ( null != config.AgentLables ) {
	   echo "Using supplied agentLables '${config.AgentLables}'"
	   AgentLabel = "${config.AgentLables}"
	}
   pipeline {
	   agent {
	      label agentLabel
	   }
	   options {
	      buildDiscarder(logRotator(numToKeepStr: '3'))
	         timestamps()
	         disableConcurrentBuilds()
	         timeout(time: globalTimeout, unit: 'MINUTES')
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
	         stage('test')
	         {
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
                    sh label: 'arduino', returnStatus: true, script: '/usr/local/arduino-cli/arduino-cli -v compile --build-path ${WORKSPACE}/target/ --fqbn "esp8266:esp8266:generall" az-envy'
	            }
	         }
            stage('Flash') {
               agent { label 'ARM' }
               steps {
                  echo 'Flash....'
                  unstash 'arduino'
                  sh label: 'arduino', returnStatus: true, script: '/usr/local/arduino-cli/arduino-cli core install esp8266:esp8266'
                  sh label: 'arduino', returnStatus: true, script: '/usr/local/arduino-cli/arduino-cli -v upload -i target/az-envy.ino.bin --fqbn "esp8266:esp8266:generall" -p /dev/ttyUSB0'
               }
            }

	      } //END STAGES
	      post 
	      {
	         always {
	            // delete moved to stage deploy
	               echo "Pipeline run finished"
	         }
	      }
	} //Pipeline
   } //body

