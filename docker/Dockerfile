FROM kojiromike/dind
MAINTAINER jhalterman@gmail.com

ENV COPYCAT_GIT_URL http://github.com/kuujo/copycat
ENV FIGARO_GIT_URL http://github.com/jhalterman/figaro
ENV COPYCAT_JEPSEN_GIT_URL http://github.com/jhalterman/copycat-jepsen
ENV JEPSEN_GIT_URL http://github.com/aphyr/jepsen
ENV LEIN_ROOT true

# Install Jepsen dependencies
RUN apt-get -y -q install software-properties-common 
RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

RUN apt-get -y -q install software-properties-common 
RUN add-apt-repository ppa:webupd8team/java -y
RUN apt-get -y -q update
RUN echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | sudo debconf-set-selections
RUN apt-get install -qqy oracle-java8-installer oracle-java8-set-default libjna-java git gnuplot wget maven

# Install Leiningen
RUN cd / && wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && mv /lein /usr/bin 
RUN chmod +x /usr/bin/lein

# Install Copycat
RUN git clone $COPYCAT_GIT_URL /copycat
RUN cd /copycat && mvn install -DskipTests=true

# Install Figaro
RUN git clone $FIGARO_GIT_URL /figaro
RUN cd /figaro && lein install

# Clone copycat-jepsen
RUN git clone $COPYCAT_JEPSEN_GIT_URL /copycat-jepsen

# Install Jepsen
RUN git clone $JEPSEN_GIT_URL /jepsen
RUN cd /jepsen/jepsen && lein install

# Install the docker-jepsen build script
ADD ./build-docker-jepsen.sh /usr/local/bin/build-docker-jepsen.sh
RUN chmod +x /usr/local/bin/build-docker-jepsen.sh

ADD ./.bashrc /root/.bashrc