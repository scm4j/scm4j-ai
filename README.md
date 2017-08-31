[![Build Status](https://travis-ci.org/scm4j/scm4j-ai.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-ai)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-ai/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-ai?branch=master)

Status: in development

# Overview
This component automates installation of products which are represented by artifacts in maven repositories. 

# Terms

- `product list`: artifact (yaml file) which lists `product artifacts` and repositories which will be used to search artifacts
- `product artifact`: artifact which describes a product. It is a yaml file which lists `component artifacts` and `component installers`
- `component installer`: is represented by class name and parameters. Class is instantiated by given name with given parameters and should implement `IAIinstaller` interface
  
# Data Structure

Ref. [data-structure.md](data-structure.md)
  
  





