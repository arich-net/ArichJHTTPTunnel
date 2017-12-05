@echo off
REM -------------------------------------
REM Windows Batch to launch JHttpTunnel
REM -------------------------------------
SET FORWARD_HOST=127.0.0.1 
SET FORWARD_PORT=60822
SET JHTTP_SERVER_LISTENPORT=8888
SET JHTTPTUNNEL_HOME=c:\cygwin64\home\23\Documents\GIT\ArichJHTTPTunnel
SET JAVA_HOME=C:\Program Files\Java\jdk1.8.0_131
SET MAVEN_HOME=%USERPROFILE%\.m2
SET M2_REPO=%MAVEN_HOME%\repository
SET M2_LOG4J=%M2_REPO%\log4j\log4j\1.2.17\log4j-1.2.17.jar
SET M2_JUNIT=%M2_REPO%\junit\junit\3.8.1\junit-3.8.1.jar

REM -------------------------------------
REM Classpath Variable
REM -------------------------------------

SET CLASSPATH=.;%JHTTPTUNNEL_HOME%\target\ArichJHTTPTunnel-1.0-0.jar;%M2_LOG4J%;%M2_JUNIT%
SET CLASSPATH=%CLASSPATH%;%JHTTPTUNNEL_HOME%\config

ECHO %JHTTPTUNNEL_HOME%
ECHO %JAVA_HOME%
ECHO %MAVEN_HOME%
ECHO %CLASSPATH%
ECHO "%FORWARD_HOST%:%FORWARD_PORT%"

"%JAVA_HOME%\bin\java" -Dforward="%FORWARD_HOST%:%FORWARD_PORT%" -Dlog4j.configuration=log4j-console.xml -cp %CLASSPATH% com.arichnet.jhttptunnel.JHttpTunnelServer %JHTTP_SERVER_LISTENPORT% 
