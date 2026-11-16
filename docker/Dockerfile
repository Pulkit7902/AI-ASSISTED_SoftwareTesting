# Build once WITH network (warms the Maven cache); run trials WITHOUT network (--network none).
FROM maven:3.9-eclipse-temurin-21
RUN useradd -m -u 10001 runner
USER runner
ENV HOME=/home/runner
WORKDIR /warm
COPY --chown=runner template/pom.xml pom.xml
RUN mkdir -p src/main/java/exp src/test/java/exp \
 && printf 'package exp;\npublic class Placeholder { public static int f(int a){ if(a>0){return a;} return -a; } }\n' > src/main/java/exp/Placeholder.java \
 && printf 'package exp;\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.*;\nclass PlaceholderTest { @Test void t(){ assertEquals(1, Placeholder.f(1)); } }\n' > src/test/java/exp/PlaceholderTest.java \
 && mvn -B -q test org.pitest:pitest-maven:mutationCoverage -Dcls=Placeholder \
 && mvn -B -q dependency:go-offline -Dcls=Placeholder || true
WORKDIR /work
