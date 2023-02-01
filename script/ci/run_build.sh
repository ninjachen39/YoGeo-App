#!/usr/bin/env sh
curl http://download.geonames.org/export/dump/allCountries.zip -o allCountries.zip
unzip allCountries.zip
mvn compile
MAVEN_OPTS="-Xmx4g" mvn exec:java -Dexec.mainClass="com.novetta.clavin.index.IndexDirectoryBuilder"
mvn package

