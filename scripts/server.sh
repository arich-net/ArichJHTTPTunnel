#!/bin/bash
JAVA_HOME=/opt/java/jdk/x64/jdk
JHTTPTUNNEL_HOME=/home/arich/Documents/ECLIPSE/Arich_Java_Projects/ArichJHTTPTunnel
M2_REPO=/home/arich/.m2/repository
M2_LOG4J=$M2_REPO/log4j/log4j/1.2.17/log4j-1.2.17.jar
M2_JUNIT=$M2_REPO/junit/junit/3.8.1/junit-3.8.1.jar
CLASSPATH=.:$JHTTPTUNNEL_HOME/target/ArichJHTTPTunnel-1.0-0.jar:$M2_LOG4J:$M2_JUNIT
CLASSPATH=$CLASSPATH:$JHTTPTUNNEL_HOME/config
FORWARD_HOST=127.0.0.1
FORWARD_PORT=60822
LISTEN_PORT=8888

$JAVA_HOME/bin/java -Dforward=$FORWARD_HOST:$FORWARD_PORT \
					-Dlog4j.configuration=log4j-console.xml \
					-cp $CLASSPATH \
					com.arichnet.jhttptunnel.JHttpTunnelServer $LISTEN_PORT

