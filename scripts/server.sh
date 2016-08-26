#!/bin/bash
#  v1.0 Ariel Vasquez

usage="
Java HttpTunnel server program.

usage:
    $(basename "$0") [-h|--help] [-L|--listen-port LISTEN_PORT] [-F|--forward-host SERVER:PORT]

where:
    -h|--help		show help for this script
    -L|--listen-port	set the local port to listen. It must be a number between 1025-65536
    -F|--forward-host	set the forward host settings [FORWARD_HOST]:[FORWARD_PORT]
"

getforwardhostvars() {
   re='^([A-Za-z0-9\.]+):([0-9]+)$'
   if [[ $1 =~ $re ]]; then
      FORWARD_HOST=${BASH_REMATCH[1]}
      FORWARD_PORT=${BASH_REMATCH[2]}
   else
      error
   fi
}

error() {
   echo "Invalid syntax"
   echo "$usage"
   exit 1
}

while [[ $# -gt 0 ]]; do
   key="$1"
   case $key in
      -h|--help)
         shift
         echo "$usage"
         exit 0
         ;;
      -L|--listen-port)
         re='^[0-9]+$'
         if [[ $2 =~ $re ]] && [ "$2" -ge 1025 -a "$2" -lt 65536 ]; then
            LISTEN_PORT=$2
            shift
         else
            error
         fi
         shift
         ;;
      -F|--forward-host)
         if [ "$2" != ""  ]; then
            FORWARD=$2
            shift
         else
            error
         fi
         shift
         ;;
      *)
         DEFAULT=$key
         shift
         ;;
   esac
done

if [ "$FORWARD" != ""  ]; then
   getforwardhostvars $FORWARD
else
   error
fi

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

$JAVA_HOME/bin/java -Dforward=$FORWARD_HOST:$FORWARD_PORT \
                    -Dlog4j.configuration=log4j-console.xml \
                    -cp $CLASSPATH \
                    com.arichnet.jhttptunnel.JHttpTunnelServer $LISTEN_PORT

