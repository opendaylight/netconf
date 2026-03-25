<rpc message-id="20" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
  <car-entry>
    <id>OLD001</id>
    <model>Skoda MB</model>
    <manufacturer>Skoda Mlada Boleslav</manufacturer>
    <category>sedan</category>
  </car-entry>
</cars>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUST002</id>
    <gender>male</gender>
    <age>28</age>
  </person>
</people>
<car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
  <car-person>
    <car-id>OLD001</car-id>
    <person-id>CUST002</person-id>
  </car-person>
</car-people>
</config>
</edit-config>
</rpc>
