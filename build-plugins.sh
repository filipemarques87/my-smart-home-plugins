#!/bin/bash

mvn clean package
#mvn source:jar install

rm ./plugins/*
mkdir ./plugins

find . -name \platform-*all.jar -exec cp '{}' './plugins/' ';'