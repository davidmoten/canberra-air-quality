#!/bin/bash
set -e 
set -x
mvn clean install
cd ../davidmoten.github.io
git pull
mkdir -p canberra-air-quality
cd -
cp target/*.png ../davidmoten.github.io/canberra-air-quality
cd ../davidmoten.github.io
git commit -am "upgrade air quality pngs"
git push

