FROM kojiromike/dind
MAINTAINER jhalterman@gmail.com

ENV ATOMIX_GIT_URL http://github.com/atomix/atomix
ENV TRINITY_GIT_URL http://github.com/jhall11/trinity
ENV ATOMIX_JEPSEN_GIT_URL http://github.com/jhall11/atomix-jepsen
ENV JEPSEN_GIT_URL http://github.com/aphyr/jepsen
ENV LEIN_ROOT true

# Update Docker
RUN apt-get update && apt-get -y -q upgrade lxc-docker

# Install Jepsen dependencies
RUN apt-get update
RUN apt-get -y -q install software-properties-common
RUN add-apt-repository ppa:webupd8team/java -y
RUN apt-get -y -q update
RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | sudo debconf-set-selections
RUN apt-get install -qqy oracle-java8-installer oracle-java8-set-default libjna-java git gnuplot wget maven
RUN rm -rf /var/lib/apt/lists/* && rm -rf /var/cache/oracle-jdk8-installer

# Install Leiningen
RUN cd / && wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && mv /lein /usr/bin
RUN chmod +x /usr/bin/lein

# Install Atomix
RUN git clone --depth 1 $ATOMIX_GIT_URL /atomix
RUN cd /atomix && mvn install -DskipTests=true

# Install Trinity
RUN git clone --depth 1 $TRINITY_GIT_URL /trinity
RUN cd /trinity && lein install

# Clone atomix-jepsen
RUN git clone --depth 1 $ATOMIX_JEPSEN_GIT_URL /atomix-jepsen

# Install Jepsen
RUN git clone --depth 1 $JEPSEN_GIT_URL /jepsen
RUN cd /jepsen/jepsen && lein install

# Install the docker-jepsen build script
ADD ./build-docker-jepsen.sh /usr/local/bin/build-docker-jepsen.sh
RUN chmod +x /usr/local/bin/build-docker-jepsen.sh

ADD ./.bashrc /root/.bashrc
