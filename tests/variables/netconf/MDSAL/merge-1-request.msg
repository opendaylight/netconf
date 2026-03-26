<rpc message-id="2" a="64" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>TOY001</id>
    <model>Toyota Corolla</model>
    <manufacturer>Toyota</manufacturer>
    <year>2016</year>
    <category>sedan</category>
  </car-entry>
</cars>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUST001</id>
    <age>32</age>
  </person>
</people>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>TOY001</car-id>
    <person-id>CUST001</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
