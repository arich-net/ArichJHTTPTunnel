# Java HttpTunnel

This is a project that has been initiated by JCraft that I took the liberty to continue developing. This project is based on the GNU project httptunnel written and maintained by Lars Brinkhoff http://www.nocrew.org/software/httptunnel.html.

## Requirements

The main two requirements for this project to be run are:

1) Java JDK installed and a proper $JAVA_HOME environment variable set
2) MAVEN2 configured and working

## How to use it

Please follow the next steps to install it and run a basic Http Tunnel using this Java libraries.

### Install JHttpTunnel

1) Get the latest GIT project resources.

```
$ git clone https://github.com/arich-net/ArichJHTTPTunnel.git
```

2) Compile and package the project

```
$ cd ArichJHTTPTunnel
$ mvn compile
$ mvn package
```

3) Set the environment variable JHTTPTUNNEL_HOME on the root directory of the project

```
$ export JHTTPTUNNEL_HOME=$PWD
```

4) Start the Server on the port 8888 which will forward the traffic to an SSH server behind

```
$ scripts/server.sh -L 8888 -F 127.0.0.1:22
```

5) Start the client to listen on port 22222 and communicates to the remote server on port 8888

```
$ scripts/client.sh --local-port 22222 http://127.0.0.1:8888
```

6) Try to connect to SSH using the local port opened by the client side of the httptunnel

```
$ ssh -p22222 127.0.0.1
```

## Some tools for diagnosing

In order to test how the communications are flowing through the tunnel Netcad is a great tool for diagnosing it.

### Netcad without command option

If Netcad is available on our system, but the option to execute a command is not present, no panic is always possible to redirect its output to a backpipe. Please follow these steps to actually open a client-server communications with shell redirection:

1) Test downstream traffic (mainly), the server shell will be redirected to the nc client part.

SERVER:
```
$ mknod /tmp/bpipe p
$ /bin/sh 0</tmp/bpipe | nc -l 22222 1>/tmp/bpipe
```

CLIENT:
```
$ nc localhost 22222
ls
```

2) Test upstream traffic (mainly), the client will redirect its shell to the server side

SERVER:
```
$ nc -l 22222
ls
```

CLIENT:
```
$ mknod /tmp/bpipe p
$ /bin/sh 0</tmp/bpipe | nc localhost 22222 1>/tmp/bpipe
```

## Disclaimer

We are not responsible with any usage or consecuence when implementing or playing around with this project. Please be aware that you can potentially open unwanted doors that could be used by third people. Be responsible when using or testing it!.

