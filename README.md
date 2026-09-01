# dev-pivot

一个基于 Langchain4J实现的Coding Agent学习项目、通过配置化方式实现Function Toll、MCP、Skill的调用

## 启动流程
1、先在settings.json 文件中配置LLM

2、启动 JlienMain.java 或者 启动 start-cli.bat

## 技术栈

- **语言:** Java 8
- **构建工具:** Apache Maven 3.3.9+
- **编码:** UTF-8

## 项目结构

```
dev-pivot/
├── src/
│   ├── main/java/       # 应用源代码
│   └── test/java/       # 单元测试
├── pom.xml              # Maven 项目配置
└── README.md
```

## 环境要求

- JDK 1.8
- Maven 3.3.9+

## 常用命令

```bash
# 编译
mvn clean compile

# 运行测试
mvn clean test

# 打包
mvn clean package
```
