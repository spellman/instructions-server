FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/instructions-server-0.0.1-SNAPSHOT-standalone.jar /instructions-server/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/instructions-server/app.jar"]
