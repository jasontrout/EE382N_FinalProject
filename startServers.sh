#!/bin/bash

./stopServers.sh

mkdir -p server/dist

javac -d server/dist server/src/*.java

jar -cvfm server/dist/RaftServer.jar server/manifest.txt -C server/dist/ .

if ! pgrep -x rmiregistry >/dev/null
then
    echo "rmiregistry is not running. Run ./startRmiRegistry.sh"
    exit 0
fi

sleep 2

java -Dsun.rmi.transport.tcp.repsonseTimeout=200 -Dsun.rmi.transport.tcp.connectionTimeout=200 -jar server/dist/RaftServer.jar 1 &
java -Dsun.rmi.transport.tcp.repsonseTimeout=200 -Dsun.rmi.transport.tcp.connectionTimeout=200 -jar server/dist/RaftServer.jar 2 &
java -Dsun.rmi.transport.tcp.repsonseTimeout=200 -Dsun.rmi.transport.tcp.connectionTimeout=200 -jar server/dist/RaftServer.jar 3 &
java -Dsun.rmi.transport.tcp.repsonseTimeout=200 -Dsun.rmi.transport.tcp.connectionTimeout=200 -jar server/dist/RaftServer.jar 4 &
java -Dsun.rmi.transport.tcp.repsonseTimeout=200 -Dsun.rmi.transport.tcp.connectionTimeout=200 -jar server/dist/RaftServer.jar 5 &
