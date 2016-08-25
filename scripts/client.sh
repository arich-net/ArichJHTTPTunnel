#!/bin/bash
if [ "$JAVA_HOME" != "" ]; then
   JAVA_HOME="$JAVA_HOME"
else
   JAVA_HOME=/opt/java/jdk/x64/jdk
fi

if [ "$JHTTPTUNNEL_HOME" != "" ]; then
   JHTTPTUNNEL_HOME="$JHTTPTUNNEL_HOME"
else
   JHTTPTUNNEL_HOME=/home/arich/Documents/ECLIPSE/Arich_Java_Projects/ArichJHTTPTunnel
fi

M2_REPO=~/.m2/repository
M2_LOG4J=$M2_REPO/log4j/log4j/1.2.17/log4j-1.2.17.jar
M2_JUNIT=$M2_REPO/junit/junit/3.8.1/junit-3.8.1.jar
CLASSPATH=.:$JHTTPTUNNEL_HOME/target/ArichJHTTPTunnel-1.0-0.jar:$M2_LOG4J:$M2_JUNIT
CLASSPATH=$CLASSPATH:$JHTTPTUNNEL_HOME/config
#JHTTP_SERVER_HOST=192.168.1.13
JHTTP_SERVER_HOST=127.0.0.1
JHTTP_SERVER_PORT=8888
LOCAL_PORT=22222
#PROXY_HOST=192.168.1.254
#PROXY_PORT=3128

if [ "$PROXY_HOST" != "" ]; then
   $JAVA_HOME/bin/java -Dlport=$LOCAL_PORT \
                       -Dproxy=$PROXY_HOST:$PROXY_PORT \
                       -Dlog4j.configuration=log4j-console.xml \
                       -cp $CLASSPATH \
                       com.arichnet.jhttptunnel.JHttpTunnelClient $JHTTP_SERVER_HOST:$JHTTP_SERVER_PORT
else
   $JAVA_HOME/bin/java -Dlport=$LOCAL_PORT \
                       -Dlog4j.configuration=log4j-console.xml \
                       -cp $CLASSPATH \
                       com.arichnet.jhttptunnel.JHttpTunnelClient $JHTTP_SERVER_HOST:$JHTTP_SERVER_PORT
fi

