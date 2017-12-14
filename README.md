[![Build Status](https://travis-ci.org/scm4j/scm4j-deployer-engine.svg?branch=master)](https://travis-ci.org/scm4j/scm4j-deployer-engine)
[![Coverage Status](https://coveralls.io/repos/github/scm4j/scm4j-deployer-engine/badge.svg?branch=master)](https://coveralls.io/github/scm4j/scm4j-deployer-engine?branch=master)

Status: in development


# Overview
This component automates installation (deployment) of products which are represented by artifacts in maven repositories. `download` and `install` are different actions, and if we want to install product without network connection we must download product and all dependencies before installation. If `scm4j-deployer-engine` can't find valid repository in `portable` or `working folder` then they downloads from network.

# Terms

- `product list`: artifact (yaml file) which lists `products` and maven repositories
- `product`: jar-artifact whose main class has public method `getProductStructure` which returns  `product structure`
- `product structure`: lists `component's` and `dependent products` if they are exists
- `dependent products`: without which it is impossible to install the current product
- `component`: represented by  artifact coordinates and one or few `deployment procedure`
- `deployment procedure`: lists `actions`
- `action`: represented by `component deployer` class and `params`
- `component deployer`: Is instantiated during `deployment procedure`, action paremeters are passed using `init` method
- `working folder`: Used to keep downloaded components and internal data structures
- `portable folder`:  If specified used as a target for `download` command and as an implicit repository. Scenario: download all components to a `portable folder` (normally located at the USB flash drive), go to a place where internet is not presented and install products there using `portable folder` as a source
- `legacy version`: product version who deploys without `scm4j-installer`

Thus all dependencies of product artifact are "deployers" and their dependencies i.e. implement deployment  logic. Deployment "data" is represented by artifacts which are listed by `IProductStructure` interface.

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
- `listDeployedProducts`: lists all deployed product from `deployed-products.yml`

# Deployment

- Deployment result: OK, NEWER_VERSION_EXISTS, NEED_REBOOT, INCOMPATIBLE_API_VERSION, ALREADY_INSTALLED, FAILED
- INCOMPATIBLE_API_VERSION: Product should depend on `deployer-api` which is compatible with one used by engine
- DP - deployed product. Contains IProductStructure, Coords, and deployment URL
- RP - required product

Steps

- API compatibility is checked
- Previously `deployed product` (`DP`) version is queried (deployed-products.yml), if not found ILegacyProduct.`queryLegacyDeployedProduct` is used to get `DP`
- Install dependencies (If product has `dependency products`)
  - all `dependency products` are installed recursively
  - if one of `dependency products` installation fails - `DP` installation fails
  - if `dependency product` installation returns `NEED_REBOOT` - `DP` returns `NEED_REBOOT`
- If `DP`.version equals to `RP`.version then `ALREADY_INSTALLED` is returned
- If `DP`.version greater then `RP`.version then `NEWER_VERSION_EXISTS` is returned
- Stop `DP`
  - `DP` deployers and components are downloaded
  - All `DP` components are stopped in reverse order
  - If `stop` fails all `DP`-components are `disabled`,`NEED_REBOOT` is returned and all already deployed components undeploy
- Deployment
  - Components which does not exist anymore are undeployed, updated components redeployed (stop/undeploy/deploy), new components deployed
- Start
  - All components started
- If `portable folder` is specified it is implicitly used as a main repository (before all repos listed in `product list`)

## Error Handling

If error occurs during component deployment previously deployed components are stopped and undeployed (NEED_REBOOT is ignored), FAILED is returned

# Self-upgrade

Self-upgrade of product which uses `scm4j-deployer-engine` is not possible. New product with new name must be relases instead 

# Related Components

  - [scm4j-deployer-engine](../../../scm4j-deployer-engine/blob/master/README.md)
  - [scm4j-deployer-api](../../../scm4j-deployer-api/blob/master/README.md)
  - [scm4j-deployer-installers](../../../scm4j-deployer-installers/blob/master/README.md)
