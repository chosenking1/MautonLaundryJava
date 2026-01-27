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
COPY src ./src

RUN mvn -B clean package -DskipTests


### =========================
### Runtime stage (Java 25)
### =========================
FROM eclipse-temurin:25-jre-alpine

RUN mkdir -p /apps /apps/config
WORKDIR /apps

COPY --from=build /mautonLaundry/target/*.war /apps/mautonLaundry.war
COPY --from=build /mautonLaundry/src/main/resources/application.properties /apps/config/application.properties
#COPY --from=build /mautonLaundry/src/main/resources/logback.xml /apps/config/logback.xml

RUN apk add --no-cache \
    tzdata \
    curl \
    vim \
    iputils

ENV TZ=Africa/Lagos
RUN cp /usr/share/zoneinfo/$TZ /etc/localtime

VOLUME /apps/config
EXPOSE 8079

ENTRYPOINT ["java","-jar","/apps/mautonLaundry.war","--spring.config.location=file:/apps/config/application.properties"]
