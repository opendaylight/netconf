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
    <cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car" a:operation="remove">
    </cars>
    <people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:people" a:operation="remove">
    </people>
    <car-people xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car-people" a:operation="remove">
    </car-people>
    </config>
</edit-config>
</rpc>
