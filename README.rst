============================================
OpenDaylight NETCONF/RESTCONF implementation
============================================

This project hosts implementations of protocols defined by the
`IETF NETCONF Working Group <https://datatracker.ietf.org/wg/netconf/about/>`__
In particular it implements:
* `Network Configuration Protocol (NETCONF) <https://www.rfc-editor.org/rfc/rfc6241>`__
* `RESTCONF Protocol <https://www.rfc-editor.org/rfc/rfc8040>`__

Your immediate interests may be:
* Documentation is in :doc:`docs <https://docs.opendaylight.org/projects/netconf/en/latest/index.html>`
* Ready-to-applications are in :doc:`apps <apps/README.rst>`

Other that that, you may delve into gory details:
* basic project infrastructure, including :doc:`the BOM <artifacts/README.rst>`,
  :doc:`Karaf features <features/README.rst>`, :doc:`Dynamic Karaf distribution <karaf/README.rst>`,
  :doc:`Static Karaf distribution <karaf-static/README.rst>` and the :doc:`Common Maven Parent <parent/README.rst>`
* :doc:`YANG models <models/README.rst>` relating to this project
* :doc:`Transport layer <transport/README.rst>` implementation
* :doc:`Low-level <protocol/README.rst>` protocol implementations
* :doc:`High-level <plugins/README.rst>` protocol integrations
* :doc:`NETCONF Key store <keystore/README.rst>` implementation
* :doc:`NETCONF Trust store <truststore/READ.rst>` implementation
* :doc:`applications <appls/README.rst>` for both end users and integrators
