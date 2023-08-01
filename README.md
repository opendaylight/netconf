[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.opendaylight.netconf/netconf-artifacts/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.opendaylight.netconf/netconf-artifacts)
[![Javadocs](https://www.javadoc.io/badge/org.opendaylight.netconf/restconf-common.svg)](https://www.javadoc.io/doc/org.opendaylight.netconf/restconf-common)
[![License](https://img.shields.io/badge/License-EPL%201.0-blue.svg)](https://opensource.org/licenses/EPL-1.0)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=coverage)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=opendaylight_netconf&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=opendaylight_netconf)

# OpenDaylight NETCONF/RESTCONF implementation

This project hosts implementations of protocols defined by the [IETF NETCONF Working Group](https://datatracker.ietf.org/wg/netconf/about/).
In particular, it implements:
* [Network Configuration Protocol (NETCONF)](https://www.rfc-editor.org/rfc/rfc6241)
* [RESTCONF Protocol](https://www.rfc-editor.org/rfc/rfc8040)

Your immediate interests may be:
* Documentation is in [docs](https://docs.opendaylight.org/projects/netconf/en/latest/index.html)
* Ready-to-applications are in [apps](apps/README.md)

Other than that, you may delve into gory details:
* basic project infrastructure, including [the BOM](artifacts), [Karaf features](features),
[Dynamic Karaf distribution](karaf), [Static Karaf distribution](karaf-static) and the [Common Maven Parent](parent)
* [YANG models](model) relating to this project
* [Transport layer](transport) implementation
* [Low-level](protocol) protocol implementations
* [High-level](plugins) protocol integrations
* [NETCONF Key store](keystore) implementation
* [NETCONF Trust store](truststore) implementation
* [applications](apps/README.md) for both end users and integrators
