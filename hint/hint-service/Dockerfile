FROM prod.docker.system.local/baseimages/sda-jre21-alpine:76

COPY ./build/install/* /java/
COPY src/main/resources/application.yml /java/config/

RUN /tmp/setPermissions.sh
USER java

ENTRYPOINT ["/java/bin/hint-service"]
