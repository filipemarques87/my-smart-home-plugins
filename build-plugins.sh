#!/bin/bash

mvn clean package

rm ./plugins/*
mkdir ./plugins

find . -name \platform-*all.jar -exec cp '{}' './plugins/' ';'