FROM openjdk:11-jre-slim

COPY /build/libs/trading-bot-0.0.1.jar /trading-bot.jar

ENTRYPOINT ["java", "-Dconfig.file=/app/conf/application.conf", "-jar", "/trading-bot.jar"]