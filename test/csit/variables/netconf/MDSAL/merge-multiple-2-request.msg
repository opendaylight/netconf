<rpc message-id="51" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>CARMID</id>
    <model>Toyota Aurion</model>
    <manufacturer>Toyota</manufacturer>
    <year>2006</year>
    <category>sedan</category>
  </car-entry>
</cars>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUSTMID</id>
    <age>40</age>
  </person>
</people>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>CARMID</car-id>
    <person-id>CUSTMID</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
