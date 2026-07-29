#### =========================
#### Build stage (Java 25)
#### =========================
#FROM eclipse-temurin:25-jdk-alpine AS build
#
#LABEL maintainer="Joshua"
#
#RUN apk add --no-cache \
#    maven \
#    bash
#
#WORKDIR /mautonLaundry
#
#COPY pom.xml .
#COPY src ./src
#
#RUN mvn -B clean package -DskipTests
#
#
#### =========================
#### Runtime stage (Java 25)
#### =========================
#FROM eclipse-temurin:25-jre-alpine
#
#RUN mkdir -p /apps /apps/config
#WORKDIR /apps
#
#COPY --from=build /mautonLaundry/target/*.war /apps/mautonLaundry.war
#COPY --from=build /mautonLaundry/src/main/resources/application.properties /apps/config/application.properties
##COPY --from=build /mautonLaundry/src/main/resources/logback.xml /apps/config/logback.xml
#
#RUN apk add --no-cache \
#    tzdata \
#    curl \
#    vim \
#    iputils
#
#ENV TZ=Africa/Lagos
#RUN cp /usr/share/zoneinfo/$TZ /etc/localtime
#
#VOLUME /apps/config
#EXPOSE 8079
#
#ENTRYPOINT ["java","-jar","/apps/mautonLaundry.war","--spring.config.location=file:/apps/config/application.properties"]


### =========================
### Build stage (Java 25)
### =========================
FROM eclipse-temurin:25-jdk-alpine AS build

LABEL maintainer="Joshua"

RUN apk add --no-cache \
    maven \
    bash

WORKDIR /mautonLaundry

COPY pom.xml .
# lombok.config must be present at compile time, or Lombok will not copy @Lazy
# onto the generated constructor parameters -- and the UserAccessService <->
# MakerCheckerService cycle break silently disappears, failing startup. It lives
# at the repo root, so it has to be copied explicitly alongside pom.xml.
COPY lombok.config .
COPY src ./src

RUN mvn -B clean package -DskipTests


### =========================
### Runtime stage (Java 25)
### =========================
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache \
    tzdata \
    curl \
    vim \
    iputils

ENV TZ=Africa/Lagos

RUN cp /usr/share/zoneinfo/$TZ /etc/localtime

# Security
RUN addgroup -S spring && adduser -S spring -G spring

RUN mkdir -p /apps/config

WORKDIR /apps

COPY --from=build /mautonLaundry/target/*.jar app.jar

RUN chown -R spring:spring /apps

USER spring:spring

VOLUME /apps/config

EXPOSE 8079

ENTRYPOINT ["java", \
"-Dspring.profiles.active=prod", \
"-Dspring.config.additional-location=file:/apps/config/", \
"-jar", \
"/apps/app.jar"]
