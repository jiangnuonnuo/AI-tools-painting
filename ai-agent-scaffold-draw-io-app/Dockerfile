# 镜像库；https://github.com/fuzhengwei/docker-image-pusher
FROM registry.cn-hangzhou.aliyuncs.com/xfg-studio/openjdk:17-jdk-slim

# 作者
MAINTAINER xiaofuge

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/ai-agent-scaffold-draw-io-app.jar /ai-agent-scaffold-draw-io-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /ai-agent-scaffold-draw-io-app.jar $PARAMS"]