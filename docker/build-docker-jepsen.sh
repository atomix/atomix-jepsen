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
ssh $N1_IP "rm /etc/apt/apt.conf.d/docker-clean && apt-get update && apt-get install sudo net-tools wget sysvinit-core sysvinit sysvinit-utils curl vim man faketime unzip iptables iputils-ping logrotate && apt-get remove -y --purge --auto-remove systemd" 

# Export the sub-container
docker export n1 > /root/jepsennode.tar
gzip /root/jepsennode.tar