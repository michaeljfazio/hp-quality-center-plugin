# Introduction

A plugin for Jenkins CI that synchronizes Maven unit test results with HP ALM Quality Center.

* [Jenkins-CI Wiki](https://wiki.jenkins-ci.org/display/JENKINS/HP+ALM+Quality+Center+Plugin)
* [Source Code](https://github.com/S73417H/hp-quality-center)

# Current Build Status

This project uses CloudBees for continuous integration.

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/hp-quality-center-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/hp-quality-center-plugin/)

# Getting Started

## Prerequisites

- Java 6
- Maven 2.2

## Editing the code in an IDE

This project can be imported into any IDE that supports Maven.

## Developing

1. Compile and package the project:

  `mvn package`

2. Run the embedded Jenkins CI test server:

  `mvn hpi:run`

3. Open your favorite web browser and point it to:

  http://localhost:8080/jenkins

# Authors

[Michael Fazio](http://www.linkedin.com/pub/michael-fazio/b/b20/a23)

# License

This project is released under LGPL 3.0 license.
