# OpenDaylight NETCONF/RESTCONF implementation

This project hosts implementations of protocols defined by the [IETF NETCONF Working Group](https://datatracker.ietf.org/wg/netconf/about/).
In particular it implements: 
* [Network Configuration Protocol (NETCONF)](https://www.rfc-editor.org/rfc/rfc6241) 
* [RESTCONF Protocol](https://www.rfc-editor.org/rfc/rfc8040)

Your immediate interests may be: 
* Documentation is in [docs](https://docs.opendaylight.org/projects/netconf/en/latest/index.html)
* Ready-to-applications are in [apps](apps/README.rst)

Other that that, you may delve into gory details: 
* basic project infrastructure, including [the BOM](artifacts/README.rst>), [Karaf features](features/README.rst),
[Dynamic Karaf distribution](karaf/README.rst), [Static Karaf distribution](karaf-static/README.rst) and the [Common Maven Parent](parent/README.rst) 
* [YANG models](models/README.rst) relating to this project 
* [Transport layer](transport/README.rst) implementation 
* [Low-level](protocol/README.rst) protocol implementations 
* [High-level](plugins/README.rst) protocol integrations 
* [NETCONF Key store](keystore/README.rst) implementation 
* [NETCONF Trust store](truststore/READ.rst) implementation 
* [applications](apps/README.md) for both end users and integrators
