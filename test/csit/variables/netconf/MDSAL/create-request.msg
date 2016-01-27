<rpc message-id="15" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>create</default-operation>
<config>
<people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people">
  <person>
    <id>CUSTBAD</id>
    <age>99</age>
  </person>
</people>
</config>
</edit-config>
</rpc>
