#!/bin/bash
set -e 
set -x
wget -O src/test/resources/air.csv https://www.data.act.gov.au/api/views/94a5-zqnn/rows.csv?accessType=DOWNLOAD
mvn clean install
cd ../davidmoten.github.io
git pull
mkdir -p canberra-air-quality
cd -
cp target/*.png ../davidmoten.github.io/canberra-air-quality
cd ../davidmoten.github.io
gitc "upgrade air quality pngs"

