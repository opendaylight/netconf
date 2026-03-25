<rpc message-id="115" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>create</default-operation>
<config>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUSTOLD</id>
    <age>99</age>
  </person>
</people>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>CAROLD</id>
    <manufacturer>Ford</manufacturer>
    <year>1950</year>
  </car-entry>
</cars>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>CAROLD</car-id>
    <person-id>CUSTOLD</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
