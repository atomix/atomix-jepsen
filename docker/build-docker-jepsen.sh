#!/bin/sh

#gen sshkey
ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa

# Disable strict host checking
cat <<EOF > /root/.ssh/config
Host *
    StrictHostKeyChecking no
EOF

# Update docker
apt-get update && apt-get -y -q upgrade lxc-docker

# Pull latest docker image
docker pull jondoo1220/atomix_node:latest

# Start container
docker run -d --name n1 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jondoo1220/atomix_node:latest
N1_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n1)

sleep 10

# Export the sub-container
docker export n1 > /root/jepsennode.tar
gzip /root/jepsennode.tar
