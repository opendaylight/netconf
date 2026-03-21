<rpc message-id="70" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
    <target>
        <candidate/>
    </target>
    <test-option>
        set
    </test-option>
    <default-operation>replace</default-operation>
    <config>
      <cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car">
        <car-entry>
          <id>REPLACE</id>
          <model>Panda</model>
          <manufacturer>FIAT</manufacturer>
          <year>2003</year>
        </car-entry>
      </cars>
      <car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people">
        <car-person>
          <car-id>REPLACE</car-id>
          <person-id>CUSTYOUNG</person-id>
        </car-person>
      </car-people>
    </config>
</edit-config>
</rpc>
