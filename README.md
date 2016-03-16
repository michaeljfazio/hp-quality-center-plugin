# Introduction

A plugin for Jenkins CI that synchronizes Maven unit test results with HP ALM Quality Center.

# Current Build Status

This project uses Travis CI for continuous integration.

[![Build Status](https://travis-ci.org/S73417H/jenkins-qc-plugin.png)](https://travis-ci.org/S73417H/jenkins-qc-plugin)

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
