# This creates a docker image with JDKs etc. First build the uber jar and then copy to this directory.
# To build: docker build -t restabuild .
# To run: docker run -p 4000:8080 restabuild

FROM openjdk:8-jdk-alpine

RUN ["apk", "update"]
RUN ["apk", "add", "maven"]


WORKDIR /restabuild
ADD . /restabuild


EXPOSE 8080
ENTRYPOINT ["java","-jar","restabuild.jar","config.properties"]
