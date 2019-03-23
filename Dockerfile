FROM openjdk:8-jdk-alpine

COPY executor.policy /app/executor.policy
COPY lib /app/lib
COPY build/libs/kotlin-playground*-uber.jar /app/playground.jar

ENV PATH $PATH:/usr/lib/kotlinc/bin

EXPOSE 80

WORKDIR app

CMD ["java", "-jar", "playground.jar"]