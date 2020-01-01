#!/bin/bash
set -e 
set -x
wget -O src/test/resources/air.json 'https://www.data.act.gov.au/resource/94a5-zqnn.json?name=Civic&$select=datetime,aqi_pm2_5&$where=datetime%3E%272019-11-20%27&$order=datetime%20desc'

