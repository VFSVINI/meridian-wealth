# Imagem do Space do Hugging Face (SDK Docker). O workflow deploy-hf.yml publica o repositório no Space trocando o Dockerfile
# e o README da raiz por estes dois arquivos. O Space exige a porta 7860 e um usuário de UID 1000.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 1000 app && adduser -S -u 1000 -G app app
WORKDIR /app
COPY --from=build /src/target/meridian-wealth.jar app.jar
USER app
ENV PORT=7860
EXPOSE 7860
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s \
  CMD wget -qO- http://localhost:7860/actuator/health/liveness | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
