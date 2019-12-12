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

java -jar server/dist/RaftServer.jar 1 &
java -jar server/dist/RaftServer.jar 2 &
java -jar server/dist/RaftServer.jar 3 &
java -jar server/dist/RaftServer.jar 4 &
java -jar server/dist/RaftServer.jar 5 &
