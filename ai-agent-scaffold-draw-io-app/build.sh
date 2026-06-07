# 普通镜像构建，随系统版本构建 amd/arm mac 电脑可以选择第2条build方式构建
#docker build -t fuzhengwei/ai-draw-io-app:1.0 -f ./Dockerfile .

docker build --platform linux/amd64,linux/arm64 --load -t fuzhengwei/ai-draw-io-app:1.6 -f ./Dockerfile .

# 兼容 amd、arm 构建镜像
# docker buildx build --load --platform liunx/amd64,linux/arm64 -t fuzhengwei/ai-agent-scaffold-draw-io-app:1.0 -f ./Dockerfile . --push