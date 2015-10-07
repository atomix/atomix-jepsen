if [ -f ~/.ssh/id_rsa.pub ];
then

  if [ ! -f ~/.started ];
  then

    touch ~/.started

    #import the image
    cat /root/jepsennode.tar.gz | docker import - jepsennode

    # start the 5 nodes
    docker run --privileged -v /root/.m2:/root/.m2 -d --name n1 -h n1 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jepsennode /run.sh
    docker run --privileged -v /root/.m2:/root/.m2 -d --name n2 -h n2 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jepsennode /run.sh
    docker run --privileged -v /root/.m2:/root/.m2 -d --name n3 -h n3 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jepsennode /run.sh
    docker run --privileged -v /root/.m2:/root/.m2 -d --name n4 -h n4 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jepsennode /run.sh
    docker run --privileged -v /root/.m2:/root/.m2 -d --name n5 -h n5 -e ROOT_PASS="root" -e AUTHORIZED_KEYS="`cat ~/.ssh/id_rsa.pub`" jepsennode /run.sh

  else

    docker ps | grep -qw n1 || docker start n1
    docker ps | grep -qw n2 || docker start n2
    docker ps | grep -qw n3 || docker start n3
    docker ps | grep -qw n4 || docker start n4
    docker ps | grep -qw n5 || docker start n5

  fi

# note the ip address and add to /etc/hosts
N1_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n1)
N2_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n2)
N3_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n3)
N4_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n4)
N5_IP=$(docker inspect --format '{{ .NetworkSettings.IPAddress }}' n5)

N1_ID=$(docker inspect --format '{{ .Config.Hostname }}' n1)
N2_ID=$(docker inspect --format '{{ .Config.Hostname }}' n2)
N3_ID=$(docker inspect --format '{{ .Config.Hostname }}' n3)
N4_ID=$(docker inspect --format '{{ .Config.Hostname }}' n4)
N5_ID=$(docker inspect --format '{{ .Config.Hostname }}' n5)

cat <<EOF > /etc/hosts
127.0.0.1   localhost
::1     localhost ip6-localhost ip6-loopback
$N1_IP n1 $N1_ID
$N2_IP n2 $N2_ID
$N3_IP n3 $N3_ID
$N4_IP n4 $N4_ID
$N5_IP n5 $N5_ID
EOF

#setup ssh known_hosts
ssh-keyscan -t rsa n1 > ~/.ssh/known_hosts
ssh-keyscan -t rsa n2 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n3 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n4 >> ~/.ssh/known_hosts
ssh-keyscan -t rsa n5 >> ~/.ssh/known_hosts

scp /etc/hosts n1:/etc/hosts
scp /etc/hosts n2:/etc/hosts
scp /etc/hosts n3:/etc/hosts
scp /etc/hosts n4:/etc/hosts
scp /etc/hosts n5:/etc/hosts

echo ""

cat <<EOF 
Your Docker Jepsen environment is ready!
EOF

cd /atomix-jepsen

fi
