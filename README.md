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

# NETCONF/RESTCONF protocol layer assumptions

The code in this repository is organized roughly along the lines of
[NETCONF Protocol Layers](https://www.rfc-editor.org/rfc/rfc6241#page-9). For the purposes of this implementation, we are
making three distinct assuptions:
* RFC6241 Figure 1 looks like this:
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
* we read RFC8040 as if it included equivalent to Figure 1 as:
  ```
            Layer                 Example
       +-------------+      +-----------------+      +----------------+
   (4) |   Content   |      |  Configuration  |      |  Notification  |
       |             |      |      data       |      |      data      |
       +-------------+      +-----------------+      +----------------+
              |                       |                      |
       +-------------+      +-----------------+      +----------------+
   (3) | Operations  |      | PUT             |      |  Server-Sent   |
       |             |      |  /restconf/data |      |    Events      |
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
  Every HTTP Connection is tied to its underlying transport as per RFC9110 specification of the two HTTP URI schemes:
  * [http://](https://www.rfc-editor.org/rfc/rfc9110#section-4.2.1) connections are built on top of TCP transport
  * [https://](https://www.rfc-editor.org/rfc/rfc9110#section-4.2.2) connections are built on top of TLS transport
  The object model for HTTP Message comes from Netty and is catered for by the channel pipeline, including any upgrades
  from HTTP/1 in a manner similar to how NETCONF negotiates its
  [framing mechanism](https://www.rfc-editor.org/rfc/rfc6242#section-4.1) over both SSH and TLS.
  An HTTP client does not imply TCP connect, an HTTP server does not imply TCP listen -- we want to include the
  support call-home, where a network element (a RESTCONF server) is the first one to initiate the contact
  to its a network controller (a RESTCONF client).
* the two protocol implementations:
  * share the Content layer, centered around YANG Tools'
    [NormalizedNode](https://www.javadoc.io/doc/org.opendaylight.yangtools/yangtools-docs/latest/org/opendaylight/yangtools/yang/data/api/schema/NormalizedNode.html)
    and surrounding infrastructure
  * share the Secure Transport layer configuration object model
  * do each their own thing at Messages and Operations layers
  * do not aim for NETCONF over HTTP requests
  * do not aim for RESTCONF over SSH channels

# The gory details
There are sorts of things here:
* supported supported [usecases](usecases) and their integration tests
* basic project infrastructure, including [the BOM](artifacts), [Karaf features](features) and the [Common Maven Parent](parent)
* [YANG models](model) relating to this project
* [IETF Key Store](keystore/README.md) implementation
* [IETF Trust Store](truststore/README.md) implementation
* [Secure Transport layer](transport/README.md) implementation
* [Low-level](protocol/README.md) protocol implementations
* [High-level](plugins) protocol integrations, notably with MD-SAL
* a handful of unsorted things, both [NETCONF-related](netconf)
