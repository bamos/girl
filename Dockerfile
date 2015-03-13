FROM ubuntu:14.04

RUN echo "deb http://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
RUN sudo apt-get update
RUN sudo apt-get -y --force-yes install sbt
ENV GITHUB_TOKEN <token>
ADD ./ girl
EXPOSE 8585
RUN cd girl; sbt run
