[![Build Status](https://travis-ci.org/scm4j/scm4j-deployer-engine.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-deployer-engine)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-deployer-engine/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-deployer-engine?branch=master)

Status: in development


# Overview
This component automates installation (deployment) of products which are represented by artifacts in maven repositories. 

# Terms

- `product list`: yaml file which describes `products` and maven repositories
- `product`: jar-artifact whose main class has public static `getProjectStructure` method which returns  `IProductStructure` interface. IProjectStructure lists `IComponents`
- `IComponent` keeps `artifact coordinates`and lists `IInstallationProcedure`
- `IInstallationProcedure`: lists `IAction`, every `action` is represented by `installer` class and `params`. All installer classes must be in `product` dependencies.
- `installer`: class which implements `IInstaller` interface. Is instantiated during `installation procdure`, action paremeters are passed

Thus all dependencies of product artifcat are "installers" i.e. implement installation logic. Installation "data" is represented by artifacts which are listed by `IProductStructure` interface.

# Data Structure

Ref. [data-structure.md](data-structure.md)

# Scenarious

- List of available products
- List product versions
- List installed products
- Get installation tree for given product (product is downloaded first)
- Install product
  - Existing product version is queried using `Deployment URL`
  - If old version exists it is `stopped`
  - If `stop` fails all components are `disabled` and `REBOOT_NEEDED` is returned
- Downgrade
- Remove product

# Self-upgrade

Deployer 

# Manual and Legacy Installations

In some cases it is important to detect that products has been installed not using Deployer (legacy versions or manual installations)
