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
        <modules xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
            <module xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
                          <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">prefix:sal-netconf-connector</type>
                          <name>test</name>
                          <address xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">10.194.132.42</address>
                          <port xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">4000</port>
                          <username xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</username>
                          <password xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</password>
                          <tcp-only xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">false</tcp-only>
                        </module>
        </modules>
    </config>
</edit-config>
</rpc>
