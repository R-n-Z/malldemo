# 多阶段构建：编译 Java 应用
FROM maven:3.9-eclipse-temurin-17 as builder

WORKDIR /build

# 复制 pom.xml
COPY mall-master/pom.xml .
COPY mall-master/mall-common mall-common
COPY mall-master/mall-mbg mall-mbg
COPY mall-master/mall-security mall-security
COPY mall-master/mall-demo mall-demo
COPY mall-master/mall-admin mall-admin
COPY mall-master/mall-search mall-search
COPY mall-master/mall-portal mall-portal

# 构建应用：编译所有模块，确保依赖被正确解析
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建好的 JAR
COPY --from=builder /build/mall-admin/target/mall-admin-*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
COPY mall-master/mall-demo mall-demo
COPY mall-master/mall-admin mall-admin
COPY mall-master/mall-search mall-search
COPY mall-master/mall-portal mall-portal

# 构建应用：编译所有模块，确保依赖被正确解析
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建好的 JAR
COPY --from=builder /build/mall-admin/target/mall-admin-*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
