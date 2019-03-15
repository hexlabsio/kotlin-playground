FROM openjdk:8-jdk-alpine

RUN apk add --no-cache bash && \
    apk add --no-cache -t build-dependencies wget && \
    cd /usr/lib && \
    wget https://github.com/JetBrains/kotlin/releases/download/v1.3.21/kotlin-compiler-1.3.21.zip && \
    unzip kotlin-compiler-*.zip && \
    rm kotlin-compiler-*.zip && \
    rm -f kotlinc/bin/*.bat && \
    apk del --no-cache build-dependencies

COPY executor.policy /app/executor.policy
COPY lib /app/lib
COPY build/libs/kotlin-playground* /app/playground.jar

ENV PATH $PATH:/usr/lib/kotlinc/bin

EXPOSE 80

WORKDIR app

CMD ["java", "-jar", "playground.jar"]