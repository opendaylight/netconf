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
* Ready-to-use applications are in [apps](apps/README.md)

In terms of IETF standardization done as part of [NETCONF WG](https://datatracker.ietf.org/wg/netconf/), we are roughly
organized based on [NETCONF Protocol Layers](https://www.rfc-editor.org/rfc/rfc6241#page-9). For our implementation
purposes:
* NETCONF Figure 1 looks like this:
  ```
            Layer                 Example
       +-------------+      +-----------------+      +----------------+
   (4) |   Content   |      |  Configuration  |      |  Notification  |
       |             |      |      data       |      |      data      |
       +-------------+      +-----------------+      +----------------+
              |                       |                      |
       +-------------+      +-----------------+              |
   (3) | Operations  |      |  <edit-config>  |              |
       |             |      |                 |              |
       +-------------+      +-----------------+              |
              |                       |                      |
       +-------------+      +-----------------+      +----------------+
   (2) |  Messages   |      |     <rpc>,      |      | <notification> |
       |             |      |   <rpc-reply>   |      |                |
       +-------------+      +-----------------+      +----------------+
              |                       |                      |
       +-------------+      +-----------------------------------------+
   (1) |   Secure    |      |  SSH, TLS, *TCP*                        |
       |  Transport  |      |                                         |
       +-------------+      +-----------------------------------------+
  ```
  We provide TCP for completeness and logical Netty pipeline structure: a Channel corresponds to the TCP transport
  and SSH and TLS are built on top of it by adding the corresponding ChannelHandlers.
* lacking standardization, we define the corresponding RFC8040 Figure 1 to look like this:
  ```
            Layer                 Example
       +-------------+      +-----------------+      +----------------+
   (4) |   Content   |      |  Configuration  |      |  Notification  |
       |             |      |      data       |      |      data      |
       +-------------+      +-----------------+      +----------------+
              |                       |                      |
       +-------------+      +-----------------+      +----------------+
   (3) | Operations  |      | PUT             |      |  Server-Sent   |
       |             |      |  /restconf/data |      |     Events     |
       +-------------+      +-----------------+      +----------------+
              |                       |                      |
       +-------------+      +-----------------------------------------+
   (2) |  Messages   |      |  HTTP Message                           |
       |             |      |                                         |
       +-------------+      +-----------------------------------------+
              |                       |                      |
       +-------------+      +-----------------------------------------+
   (1) |   Secure    |      |  HTTP Connection                        |
       |  Transport  |      |                                         |
       +-------------+      +-----------------------------------------+
  ```
  HTTP connections are built on top of TCP transport, HTTPS connections are built on top of TLS transport, mirroring
  how things are laid out in the Netty pipeline.
* the two implementations share:
  * the Content layer
  * transport layer configuration

Other than that, you may delve into the gory details:
* basic project infrastructure, including [the BOM](artifacts), [Karaf features](features),
[Dynamic Karaf distribution](karaf), [Static Karaf distribution](karaf-static) and the [Common Maven Parent](parent)
* [YANG models](model) relating to this project
* [IETF Key Store](keystore) implementation
* [IETF Trust Store](truststore) implementation
* [Secure Transport layer](transport) implementation
* [Low-level](protocol/README.md) protocol implementations
* [High-level](plugins) protocol integrations, notably with MD-SAL
* a handful of unsorted things, both [NETCONF-related](netconf) and [RESTCONF-related](restconf)
