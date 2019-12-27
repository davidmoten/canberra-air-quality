#!/bin/bash
set -e 
set -x
wget -O src/test/resources/air.csv https://www.data.act.gov.au/api/views/94a5-zqnn/rows.csv?accessType=DOWNLOAD
git commit -am "update air.csv"
git push

