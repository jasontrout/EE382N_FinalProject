#!/bin/bash

./stop.sh

./clean.sh

mkdir -p dist

javac -d dist src/*.java

jar -cvfm dist/RaftServer.jar manifest.txt -C dist/ .

cd dist && rmiregistry &

sleep 2

java -jar dist/RaftServer.jar 1 &
java -jar dist/RaftServer.jar 2 &
java -jar dist/RaftServer.jar 3 &
java -jar dist/RaftServer.jar 4 &
java -jar dist/RaftServer.jar 5 &
