#!/bin/bash

./stop.sh

./clean.sh

mkdir -p dist

javac -d dist src/*.java

jar -cvfm dist/RaftServer.jar manifest.txt -C dist/ .

java -jar dist/RaftServer.jar 1 &
