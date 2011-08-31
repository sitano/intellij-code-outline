#!/bin/sh

mvn install:install-file -Dfile=openapi-ic-108-ss.jar -DgroupId=com.intellij -DartifactId=openapi -Dversion=10.8-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=idea-ic-108-ss-oai.jar -DgroupId=com.intellij -DartifactId=idea-openapi -Dversion=10.8-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=util-ic-108-ss-oai.jar -DgroupId=com.intellij -DartifactId=util-openapi -Dversion=10.8-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=extensions-ic-108-ss-oai.jar -DgroupId=com.intellij -DartifactId=ext-openapi -Dversion=10.8-snapshot -Dpackaging=jar
