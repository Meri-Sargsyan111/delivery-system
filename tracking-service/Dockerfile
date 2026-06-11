FROM maven:3.9.4-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests
RUN mv target/*.jar target/app.jar
CMD ["java", "-jar", "target/app.jar"]