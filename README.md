[![Build Status](https://travis-ci.org/scm4j/scm4j-ai.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-ai)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-ai/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-ai?branch=master)

Status: in development

# Overview
This component automates installation (deployment) of products which are represented by artifacts in maven repositories. 

# Terms

- `product list`: yaml filr which describes `products` and maven repositories
- `product`: jar-artifact whose main class implements `IProduct` interface. Describes `components`
- `component` is represented by `artifact coordinates`, and `installation procedure`
- `installation procedure`: list of `actions`, every `action` is represented by `installer` class name, and `params` (actually a map). All installer classes must be in `product` dependencies.
- `installer`: class which implements `IInstaller` interface. Is instantiated during `installation procdure`, action paremeters are passed


# Data Structure

Ref. [data-structure.md](data-structure.md)
  
  





