#!/usr/bin/env bash

sbt clean coverage test coverageReport coveralls

cp -r target/scala-2.12/scoverage-report target

echo "See ./target/scoverage-report/index.html"
open ./target/scoverage-report/index.html