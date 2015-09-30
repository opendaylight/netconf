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
        <modules xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
            <module xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
              <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">prefix:sal-netconf-connector</type>
              <name>test</name>
              <address xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">10.194.132.42</address>
              <port xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">2022</port>
              <username xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</username>
              <password xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">admin</password>
              <tcp-only xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">false</tcp-only>
              <event-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
                <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:netty">prefix:netty-event-executor</type>
                <name>global-event-executor</name>
              </event-executor>
              <binding-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
                <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:binding">prefix:binding-broker-osgi-registry</type>
                <name>binding-osgi-broker</name>
              </binding-registry>
              <dom-registry xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
                <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom">prefix:dom-broker-osgi-registry</type>
                <name>dom-broker</name>
              </dom-registry>
              <client-dispatcher xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
                <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:config:netconf">prefix:netconf-client-dispatcher</type>
                <name>global-netconf-dispatcher</name>
              </client-dispatcher>
              <processing-executor xmlns="urn:opendaylight:params:xml:ns:yang:controller:md:sal:connector:netconf">
                <type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool">prefix:threadpool</type>
                <name>global-netconf-processing-executor</name>
              </processing-executor>
            </module>
        </modules>
    </config>
</edit-config>
</rpc>
