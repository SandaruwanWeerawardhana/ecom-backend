@echo off
SET JAVA_HOME=C:\Users\Acer\.jdks\graalvm-jdk-21.0.8
SET PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java: %JAVA_HOME%
java -version
echo.
echo Starting Spring Boot Application...
mvn spring-boot:run -DskipTests

