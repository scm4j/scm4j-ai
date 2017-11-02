[![Build Status](https://travis-ci.org/scm4j/scm4j-deployer-engine.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-deployer-engine)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-deployer-engine/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-deployer-engine?branch=master)

Status: in development


# Overview
This component automates installation (deployment) of products which are represented by artifacts in maven repositories. `download` and `install` are different actions, so all dependencies might be downloaded first and then installed (after some time).

# Terms

- `product list`: artifact (yaml file) which describes `products` and maven repositories
- `product`: jar-artifact whose main class has public static `getProductStructure` method which returns  `product structure`
- `product structure`: lists `component`s
- `component`: represented by  artifact coordinates and one or few `deployment procedure`
- `deployment procedure`: lists `actions'
- `action`: represented by `component deployer` class and `params` 
- `component deployer`: Is instantiated during `deployment procedure`, action paremeters are passed using `init` method. All deployer classes must be in `product` dependencies.
- `working folder`: Used to keep downloaded components and internal data structures
- `portable folder`:  If specified used as a target for `download` command and as an implicit repository. Scenario: download all components to a `portable folder` (normally located at the USB flash drive), go to a place where internet is not presented and install products there using `portable folder` as a source

Thus all dependencies of product artifcat are "deployers" and their dependencies i.e. implement deployment  logic. Deployment "data" is represented by artifacts which are listed by `IProductStructure` interface.

# Data Structure

Ref. [data-structure.md](data-structure.md)

# Scenarious Overview

Scenarious are represeneted by methods of `DeployerEngine`

- `DeployerEngine`: Constructor does NOT do any network operation
- `listProducts`: gets data from offline cache of `product list`
- `refreshProducts`: refreshes cache for `listProducts`
- `listProductVersions`: gets data from offline cache (products-versions.yml)
- `refreshProductVersions`: refreshes offline cache
- `download`: downloads given product
- `deploy`: deploys given product
- `listDeployedProducts`: lists all deployed product from `Deployed products registry`

# Deployment

- Existing product version is queried using `listDeployedProducts`, if not found  IProjectStructure.`queryLegacyVersion()` is used.
- If legacy version exists it is removed by IProjectStructure.removeLegacyVersion (REBOOT_NEEDED)
- If old version exists and upgrade is needed
  - `IProjectStructure` is asked which version could uninstall old version (`uninstaller version`)
  - Uninstaller version is downloaded, if needed
  - Old version is stopped
  - If `stop` fails all components are `disabled` and `REBOOT_NEEDED` is returned
  - Old version uninstalled
- New version is installed
- If `portable folder` is specified it is implicitly used as a main repository (before all repos listed in `product list`)

# Self-upgrade

Deployer

# Manual and Legacy Installations

In some cases it is important to detect that products has been installed not using Deployer (legacy versions or manual installations)
