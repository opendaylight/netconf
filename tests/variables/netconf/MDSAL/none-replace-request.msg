<rpc message-id="m-1" a="64" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
    <edit-config>
        <target>
            <candidate/>
        </target>
        <default-operation>none</default-operation>
        <config>
            <cars xmlns="urn:opendaylight:params:xml:ns:yang:controller:config:sal-clustering-it:car"
                    xmlns:a="urn:ietf:params:xml:ns:netconf:base:1.0" a:operation="replace">
                <car-entry>
                    <id>connector</id>
                </car-entry>
            </cars>
        </config>
    </edit-config>
</rpc>
