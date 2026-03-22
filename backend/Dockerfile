FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/alms-pro-2.0.0.jar app.jar
EXPOSE 8080
CMD ["java", "-Xmx300m", "-Xms100m", "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=100m", "-XX:CompressedClassSpaceSize=50m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]