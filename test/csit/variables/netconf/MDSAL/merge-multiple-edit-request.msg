<rpc message-id="abc" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
    <target>
        <candidate/>
    </target>
    <test-option>
        set
    </test-option>
    <default-operation>merge</default-operation>
    <config>
      <cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
        <car-entry>
          <id>test</id>
          <model>Dixi</model>
          <manufacturer>BMW</manufacturer>
          <year>1928</year>
        </car-entry>
      </cars>
    </config>
</edit-config>
</rpc>
