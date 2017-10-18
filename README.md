[![Build Status](https://travis-ci.org/scm4j/scm4j-deployer-engine.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-deployer-engine)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-deployer-engine/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-deployer-engine?branch=master)

Status: in development


# Overview
This component automates installation (deployment) of products which are represented by artifacts in maven repositories. `download` and `install` are different actions, so all dependencies might be downloaded first and then installed (after some time).

# Terms

- `product list`: artifact (zipped yaml file) which describes `products` and maven repositories
- `product`: jar-artifact whose main class has public static `getProjectStructure` method which returns  `IProductStructure` interface. IProjectStructure lists `IComponents`
- `IComponent` keeps `artifact coordinates`and lists `IInstallationProcedure`
- `IInstallationProcedure`: lists `IAction`, every `action` is represented by `installer` class and `params`. All installer classes must be in `product` dependencies.
- `installer`: class which implements `IInstaller` interface. Is instantiated during `installation procdure`, action paremeters are passed

Thus all dependencies of product artifcat are "installers" i.e. implement installation logic. Installation "data" is represented by artifacts which are listed by `IProductStructure` interface.

# Data Structure

Ref. [data-structure.md](data-structure.md)

# Scenarious Overview

Scenarious are represeneted by methods of `DeployerEngine`

- `DeployerEngine`: initializes engine with URL of `product list` artifact and `working folder`. Constructor does NOT do any network operation.
- `listProducts`: gets data from offline cache
- `refreshProducts`: refreshes cache for `listProducts`
- `listProductVersions`: gets data from offline cache
- `refreshProductVersions`: refreshes offline cache
- `download`: downloads given product
- `deploy`: deploys given product
- `listDeployedProducts`: lists all deployed product

# Deployment

- Existing product version is queried using `IProjectStructure`
- If old version exists and upgrade is needed 
  - `IProjectStructure` is asked which version could uninstall old version (uninstaller version)
  - Uninstaller version is downloaded, if needed
  - Old version is stopped
  - If `stop` fails all components are `disabled` and `REBOOT_NEEDED` is returned
  - Old version uninstalled
- New version is installed

# Self-upgrade

Deployer

# Manual and Legacy Installations

In some cases it is important to detect that products has been installed not using Deployer (legacy versions or manual installations)
