#!/bin/bash
set -e 
set -x
wget -O src/test/resources/air.csv https://www.data.act.gov.au/api/views/94a5-zqnn/rows.csv?accessType=DOWNLOAD
git commit -am "update air.csv"
git push
mvn clean install
cd ../davidmoten.github.io
git pull
mkdir -p canberra-air-quality
cd -
cp target/*.png ../davidmoten.github.io/canberra-air-quality
cd ../davidmoten.github.io
git commit -am "upgrade air quality pngs"
git push

