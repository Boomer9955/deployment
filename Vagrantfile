# -*- mode: ruby -*-

Vagrant.configure("2") do |config|
    config.vm.box = "base"
    #hostname виртуальной машины
    config.vm.define "bionic" do |bionic|
        bionic.vm.hostname = "192.168.0.130-test"
        bionic.vm.box = "bento/ubuntu-20.04"
        bionic.vm.network :public_network, ip: "192.168.0.130", bridge: "Realtek Gaming 2.5GbE Family Controller"
        bionic.vm.provider "virtualbox" do |bionicv|
          bionicv.memory = 8096
          bionicv.cpus = 6
        end
    end
    #shell command
    config.vm.provision "shell", inline: "echo Start"
    #ansible_local and ansible
    config.vm.provision "ansible_local" do |ansible|
        ansible.verbose = "v"
        ansible.playbook = "yml/server-creation.yml"
    end
end
