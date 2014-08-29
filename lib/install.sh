#!/bin/sh

mvn install:install-file -Dfile=annotations.jar -DgroupId=com.intellij -DartifactId=annotations -Dversion=123-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=openapi.jar -DgroupId=com.intellij -DartifactId=openapi -Dversion=123-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=idea.jar -DgroupId=com.intellij -DartifactId=idea -Dversion=123-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=util.jar -DgroupId=com.intellij -DartifactId=util -Dversion=123-snapshot -Dpackaging=jar
mvn install:install-file -Dfile=extensions.jar -DgroupId=com.intellij -DartifactId=extensions -Dversion=123-snapshot -Dpackaging=jar
