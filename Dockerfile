# 1. ビルド用（Java 21対応版に変更）
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. 実行用（こちらもJava 21に合わせます）
FROM eclipse-temurin:21-jdk-focal
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]