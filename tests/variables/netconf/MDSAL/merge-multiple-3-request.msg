<rpc message-id="52" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>CAROLD2</id>
    <model>Camry</model>
    <manufacturer>Toyota</manufacturer>
    <year>1982</year>
  </car-entry>
</cars>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUSTOLD2</id>
    <age>65</age>
  </person>
</people>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>CAROLD2</car-id>
    <person-id>CUSTOLD2</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
