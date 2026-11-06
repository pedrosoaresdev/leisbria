FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 pandecta
WORKDIR /app
COPY --from=build /app/target/pandecta-backend-*.jar app.jar
USER pandecta
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
