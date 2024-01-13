FROM openjdk:8-jdk-alpine

# 设置工作目录
WORKDIR /app


CMD ["java",  "-Xmx1024m", "-Xms1024m", "-Dfile.encoding=utf-8",   "-Dspring.profiles.active=test", "-jar", "dht-server.jar"]
