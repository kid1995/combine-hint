FROM prod.docker.system.local/baseimages/sda-jre21-alpine:94

COPY ./build/install/* /java/
COPY ./build/install/spring-boot-blueprint/lib/splunk-otel-javaagent*.jar /splunk-otel-javaagent.jar
COPY ./src/main/resources/application.yml /java/config/
COPY ./src/main/resources/application-KoP.yml /java/config/

RUN /tmp/setPermissions.sh
USER java

ENTRYPOINT ["/java/bin/hint-service"]
