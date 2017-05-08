#!/bin/bash
#  v1.0 Ariel Vasquez

usage="
Java HttpTunnel client program.

usage:
    $(basename "$0") [-h|--help] [-L|--local-port LOCALPORT] [-P|--proxy-host PROXYHOST] [-T|--enable-tls] [-P|--keystore-pass PASSWORD] (http|https)://[SERVER]:[PORT]

where:
    -h|--help		show help for this script
    -L|--local-port	set the local port to listen. It must be a number between 1025-65536
    -P|--proxy-host	set the proxy settings http://[PROXY_HOST]:[PROXY_PORT]
    -T|--enable-tls     enable TLS for jhttptunnel server component
    -P|--keystore-pass  set the keystore password
"

getservervars() {
   re='^(http|https)://([A-Za-z0-9\.]+):([0-9]+)$'
   if [[ $1 =~ $re ]]; then
      JHTTP_SERVER_HOST=${BASH_REMATCH[2]}
      JHTTP_SERVER_PORT=${BASH_REMATCH[3]}
   else
      error
   fi
}

TLS=false

getproxyvars() {
   re='^http://([A-Za-z0-9\.]+):([0-9]+)$'
   if [[ $1 =~ $re ]]; then
      PROXY_HOST=${BASH_REMATCH[1]}
      PROXY_PORT=${BASH_REMATCH[2]}
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
      -L|--local-port)
         re='^[0-9]+$'
         if [[ $2 =~ $re ]] && [ "$2" -ge 1025 -a "$2" -lt 65536 ]; then
            LOCAL_PORT=$2
            shift
         else
            error
         fi
         shift
         ;;
      -P|--proxy-host)
         if [ "$2" != ""  ]; then
            PROXY=$2
            shift
         else
            error
         fi
         shift
         ;;
      -T|--enable-tls)
         TLS=true
         shift
         ;;
      -P|--keystore-pass)
         if [ "$2" != "" ]; then
            PASSWORD=$2
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

getservervars $DEFAULT
if [ "$PROXY" != ""  ]; then
   getproxyvars $PROXY
fi

if [ "$PASSWORD" == "" ]; then
   PASSWORD="1234567890"
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

if [ "$JHTTP_SERVER_HOST" == "" -o "$JHTTP_SERVER_PORT" == "" ]; then
   error
fi

M2_REPO=~/.m2/repository
M2_LOG4J=$M2_REPO/log4j/log4j/1.2.17/log4j-1.2.17.jar
M2_JUNIT=$M2_REPO/junit/junit/3.8.1/junit-3.8.1.jar
CLASSPATH=.:$JHTTPTUNNEL_HOME/target/ArichJHTTPTunnel-1.0-0.jar:$M2_LOG4J:$M2_JUNIT
CLASSPATH=$CLASSPATH:$JHTTPTUNNEL_HOME/config

if [ "$PROXY_HOST" != "" ]; then
   $JAVA_HOME/bin/java -Dlport=$LOCAL_PORT \
                       -Dproxy=$PROXY_HOST:$PROXY_PORT \
                       -Dlog4j.configuration=log4j-console.xml \
                       -cp $CLASSPATH \
                       com.arichnet.jhttptunnel.JHttpTunnelClient $JHTTP_SERVER_HOST:$JHTTP_SERVER_PORT
else
   if $TLS; then
      $JAVA_HOME/bin/java -Dlport=$LOCAL_PORT \
                          -Dlog4j.configuration=log4j-console.xml \
                          -Dssl=true \
                          -Dkspass="$PASSWORD" \
                          -cp $CLASSPATH \
                          com.arichnet.jhttptunnel.JHttpTunnelClient $JHTTP_SERVER_HOST:$JHTTP_SERVER_PORT
   else
      $JAVA_HOME/bin/java -Dlport=$LOCAL_PORT \
                          -Dlog4j.configuration=log4j-console.xml \
                          -cp $CLASSPATH \
                          com.arichnet.jhttptunnel.JHttpTunnelClient $JHTTP_SERVER_HOST:$JHTTP_SERVER_PORT
   fi
fi

