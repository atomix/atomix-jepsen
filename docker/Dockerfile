FROM kojiromike/dind
MAINTAINER jhalterman@gmail.com

ENV COPYCAT_GIT_URL http://github.com/kuujo/copycat
ENV FIGARO_GIT_URL http://github.com/jhalterman/figaro
ENV COPYCAT_JEPSEN_GIT_URL http://github.com/jhalterman/copycat-jepsen
ENV JEPSEN_GIT_URL http://github.com/aphyr/jepsen
ENV LEIN_ROOT true

# Install Jepsen dependencies
RUN apt-get -y -q install software-properties-common 
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get -y -q update
RUN apt-get install -qqy openjdk-8-jdk libjna-java git gnuplot wget maven

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