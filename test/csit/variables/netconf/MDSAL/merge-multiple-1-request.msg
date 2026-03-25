<rpc message-id="50" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>CARYOUNG</id>
    <model>Toyota Prius</model>
    <manufacturer>Toyota</manufacturer>
    <year>2016</year>
    <category>sedan</category>
  </car-entry>
</cars>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUSTYOUNG</id>
    <age>27</age>
  </person>
</people>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>CARYOUNG</car-id>
    <person-id>CUSTYOUNG</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
