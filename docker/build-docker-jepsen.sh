#!/bin/sh

#gen sshkey
ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa

# Disable strict host checking
cat <<EOF > /root/.ssh/config
Host *
    StrictHostKeyChecking no
EOF

# Start container
docker run -d --name n1 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" tutum/debian:jessie
N1_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n1)

sleep 10

# Initial setup
ssh $N1_IP "rm /etc/apt/apt.conf.d/docker-clean && apt-get install sudo"

# Install JDK 8
ssh $N1_IP "echo 'deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main' | tee /etc/apt/sources.list.d/webupd8team-java.list"
ssh $N1_IP "echo 'deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main' | tee -a /etc/apt/sources.list.d/webupd8team-java.list"
ssh $N1_IP "echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections"
ssh $N1_IP "apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886 && apt-get update -y -q && apt-get install -qqy oracle-java8-installer oracle-java8-set-default"

# Install other dependencies and remove systemd
ssh $N1_IP "apt-get install -qqy git maven net-tools wget sysvinit-core sysvinit sysvinit-utils curl vim man faketime unzip iptables iputils-ping logrotate && apt-get remove -y --purge --auto-remove systemd" 

# Export the sub-container
docker export n1 > /root/jepsennode.tar
gzip /root/jepsennode.tar