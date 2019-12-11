#!/bin/bash

mkdir -p client/dist

javac -d client/dist client/src/*.java

jar -cvfm client/dist/RaftClient.jar client/manifest.txt -C client/dist/ .

if ! pgrep -x rmiregistry >/dev/null
then
    echo "rmiregistry is not running. Run ./startRmiRegistry.sh."
    exit 0
fi

java -jar client/dist/RaftClient.jar
