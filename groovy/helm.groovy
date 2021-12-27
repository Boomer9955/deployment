#!/usr/bin/env groovy
import groovy.json.*
import groovy.yaml.*
import groovy.*
import org.yaml.snakeyaml.Yaml

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

stage('New version Helm chart'){
    if(vHelmChart.toBoolean()){
        dir("${WORKSPACE}/chart_main"){
            git changelog: false, poll: false, credentialsId: "$env.credgitc", url: "$env.urlchart", branch: 'main'
            withCredentials([usernamePassword(credentialsId: 'helmchart', passwordVariable: 'gitpass', usernameVariable: 'gitlogin')]) {
                dir("${WORKSPACE}/chart_main/prdjango"){
                    sh "ls -al"
                    sh "pwd"
                    def filename = "Chart.yaml"
                    def read = readYaml file: "$filename"
                    read.version="${BUILD_NUMBER}"
                    read.appVersion="${BUILD_NUMBER}"
                    sh "rm $filename"
                    writeYaml file: "$filename", data: read
                    println "${read}"
                }
                sh "helm package prdjango/ --destination .deploy"
                sh """ git config --global user.email '<>' && git config --global user.name 'Jenkins Jobs'"""
                sh """git add * && git commit -m '${BUILD_NUMBER}'"""
                sh """ git push https://${gitlogin}:${gitpass}@github.com/Boomer9955/helmcharts.git main:main"""
            }
        }
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
                sh "helm repo add helmcharts https://boomer9955.github.io/helmcharts/"
                sh "helm search repo helmcharts"
                sh """ansible-playbook --private-key ${keyansible} -u ${vagrant} -i yml/hosts.yml --extra-vars "ONEHOST=${hostserver} build_number=${BUILD_NUMBER} docker_login=${dockeruser} docker_pass=${dockerpassword} helm_command=${helm_command} name_space=${NameSpace}" yml/django.yml --tags ${tags}"""
                deleteDir()
            }
        }
    }
}


