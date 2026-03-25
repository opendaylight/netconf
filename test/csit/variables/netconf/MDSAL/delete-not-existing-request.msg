<rpc message-id="6" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
    <target>
        <candidate/>
    </target>
    <test-option>
        set
    </test-option>
    <default-operation>none</default-operation>
    <config xmlns:a="urn:ietf:params:xml:ns:netconf:base:1.0">
      <cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car" a:operation="delete">
      </cars>
    </config>
</edit-config>
</rpc>
