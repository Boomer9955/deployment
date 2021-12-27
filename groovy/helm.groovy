#!/usr/bin/env groovy

registry = "boomer9955/mydjango"
env.registryCredential = 'dockerhub'
dockerImage = ''
env.credgitc='mygit'
env.urlapp='https://github.com/Boomer9955/myprojects.git'
env.urlconf='https://github.com/Boomer9955/deployment.git'
env.urlchart='https://github.com/Boomer9955/helmcharts.git'


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

stage('Building and push'){
    if(+vHelmChart.toBoolean()){
        dir("${WORKSPACE}/chart"){
            git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlchart", branch: 'main'
            dir("${WORKSPACE}/chart/helmcharts/prdjango"){
                sh "ls -al"
                def read = readYaml file: "Chart.yaml"
                read.version=${BUILD_NUMBER}
                read.appVersion=${BUILD_NUMBER}
                writeYaml file: "Chart.yaml", data: read
                println "${read}"
            }
        }
    }
}


stage('server'){
    dir("${WORKSPACE}/config"){
        withCredentials([sshUserPrivateKey(credentialsId: 'localhost', keyFileVariable: 'keyansible', passphraseVariable: '', usernameVariable: 'login')]) {
            withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'dockerpassword', usernameVariable: 'dockeruser')]) {
                sh """ansible-playbook --private-key ${keyansible} -u ${login} -i yml/hosts.yml --extra-vars "ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} helm_command=${helm_command} name_space=${NameSpace}" yml/django.yml --tags ${tags}"""
            }
        }
    }
}