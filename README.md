[![Build Status](https://travis-ci.org/scm4j/scm4j-ai.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-ai)
[![Coverage Status](https://coveralls.io/repos/scm4j/scm4j-ai/badge.png)](https://coveralls.io/r/scm4j/scm4j-ai)

# Overview
This component automates installation of a product which represents by artifacts in maven repositories. 

# Terms

- `product list`: artifact (yaml file) which lists `product artifacts` and repositories which will be used to search for artifacts
- `product artifact`: artifact (yaml file) which lists `component artifacts` and `component installers`
- `component installer`: is represented by class name and parameters. Class is instantiated with given parameters and should implement `IAIinstaller` interface
  
# Data Structure

Ref. [data-structure.md](data-structure.md)
  
  





