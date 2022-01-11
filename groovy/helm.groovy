#!/usr/bin/env groovy

registry = "boomer9955/mydjango"
env.registryCredential = 'dockerhub'
dockerImage = ''
env.credgitc='mygit'
env.urlapp='https://github.com/Boomer9955/myprojects.git'
env.urlconf='https://github.com/Boomer9955/deployment.git'
env.urlchart='https://github.com/Boomer9955/helmcharts.git'
env.chart_name='https://boomer9955.github.io/helmcharts/'

dir("${WORKSPACE}"){
    deleteDir()
}

dir("${WORKSPACE}/config"){
    git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlconf", branch: 'application'
}

stage('Building and push'){
    if(newimages.toBoolean()){
        dir("${WORKSPACE}/project"){
            git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlapp", branch: 'djangosql'
            dir("${WORKSPACE}/project/djangosql/proger"){
                sh "ls -al"
                dockerImage = docker.build registry + ":${BUILD_NUMBER}"
                docker.withRegistry( '', env.registryCredential){
                    dockerImage.push()
                }
                sh """ docker rmi ${registry}:${BUILD_NUMBER} """
            }
        }
    }
}

stage('New version Helm chart'){
    if(vHelmChart.toBoolean()){
        dir("${WORKSPACE}/chart_main"){
            git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlchart", branch: 'main'
            withCredentials([usernamePassword(credentialsId: 'helmchart', passwordVariable: 'gitpass', usernameVariable: 'gitlogin')]) {
                dir("${WORKSPACE}/chart_main/prdjango"){
                    sh "ls -al"
                    sh "pwd"
                    // Chart изменения
                    def chart_f = "Chart.yaml"
                    def chart_read = readYaml file: "$chart_f"
                    chart_read.version="${BUILD_NUMBER}"
                    chart_read.appVersion="${BUILD_NUMBER}"
                    sh "rm $chart_f"
                    writeYaml file: "$chart_f", data: chart_read
                    println "${chart_read}"
                    // values изменения
                    withCredentials([string(credentialsId: 'secret_django', variable: 'SECRET')]) {
                        def values_f = "values.yaml"
                        def values_read = readYaml file: "$values_f"
                        values_read.secret_key="${SECRET}"
                        sh "rm $values_f"
                        writeYaml file: "$values_f", data: values_read
                        println "${values_read}"
                    }
                }
                // create helmchart
                sh "helm package prdjango/ --destination .deploy"
                dir("${WORKSPACE}/chart_main/prdjango"){
                    // values меняем обратно
                    sh "ls -al"
                    sh "pwd"
                    def values_o = "values.yaml"
                    def values_back = readYaml file: "$values_o"
                    values_back.secret_key="1"
                    sh "rm $values_o"
                    writeYaml file: "$values_o", data: values_back
                    println "${values_back}"
                }
                // Push
                sh """ git config --global user.email '<>' && git config --global user.name 'Jenkins Jobs'"""
                sh """git add * && git commit -m '${BUILD_NUMBER}'"""
                sh """ git push https://${gitlogin}:${gitpass}@github.com/Boomer9955/helmcharts.git main:main"""
            }
        }
        // create and push helmchart
        dir("${WORKSPACE}/chart_gh-pages"){
            git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlchart", branch: 'gh-pages'
            withCredentials([usernamePassword(credentialsId: 'helmchart', passwordVariable: 'gitpass', usernameVariable: 'gitlogin')]) {
                sh "cr upload -o ${gitlogin} -r helmcharts -p ${WORKSPACE}/chart_main/.deploy -t ${gitpass}"
                sh "cr index -o ${gitlogin} --charts-repo https://boomer9955.github.io/helmcharts/ --git-repo helmcharts --package-path ${WORKSPACE}/chart_main/.deploy --token ${gitpass} -i index.yaml"
                sh """ git config --global user.email '<>' && git config --global user.name 'Jenkins Jobs'"""
                sh """git add * && git commit -m '${BUILD_NUMBER}'"""
                sh """ git push https://${gitlogin}:${gitpass}@github.com/Boomer9955/helmcharts.git gh-pages:gh-pages"""
            }
        }
    }
}


stage('server'){
    dir("${WORKSPACE}/config"){
        withCredentials([sshUserPrivateKey(credentialsId: 'myserverdjango', keyFileVariable: 'keyansible', passphraseVariable: '', usernameVariable: 'vagrant')]) {
            withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'dockerpassword', usernameVariable: 'dockeruser')]) {
                sh "helm repo add helmcharts $chart_name"
                if(RollbackHelmV){
                    sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml -e 'ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} script_string="helm history ${NameSpace}" name_space=${NameSpace}' yml/django.yml --tags CommandHelm"""
                    def userInput = input(id: 'Proceed1', message: 'Подтверждение отката',  parameters: [[$class: 'StringParameterDefinition', name: 'myparam', defaultValue: '']])
                    echo 'userInput: ' + userInput
                    
                    if(userInput == "$userInput") {
                        sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml --extra-vars "ONEHOST=${hostserver} name_space=${NameSpace} number_release=${userInput}" yml/django.yml --tags rollbackHelm"""
                    } else {
                        echo "Action was aborted."
                    }
                }else{
                    sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml -e 'ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} script_string="${helm_command}" name_space=${NameSpace}' yml/django.yml --tags ${tags}"""
                }
                if(vHelmChart.toBoolean()){
                    sleep 60
                    for(i=0;i<5;i++) {
                        sh "helm repo update"
                        sh "helm search repo helmcharts"
                        r = sh script: "helm search repo mydjango --version '${BUILD_NUMBER}' | grep '${BUILD_NUMBER}'", returnStatus: true
                        println "$r"
                        if (r == 0){
                            sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml -e 'ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} script_string="${helm_command}" name_space=${NameSpace}' yml/django.yml --tags helminstall"""
                            break
                        }else{
                            sleep 30
                        }
                    }
                }else{
                    sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml -e 'ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} script_string="${helm_command}" name_space=${NameSpace}' yml/django.yml --tags ${tags}"""
                }
            }
        }
    }
}


dir("${WORKSPACE}"){
    deleteDir()
}
