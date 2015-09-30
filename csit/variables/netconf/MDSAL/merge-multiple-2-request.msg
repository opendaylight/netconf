<rpc message-id="51" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<modules xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
<module>
<type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl">prefix:threadfactory-naming</type>
<name>name3</name>
<name-prefix xmlns="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl">remote-connector-processing-executor</name-prefix>
</module>
</modules>
</config>
</edit-config>
</rpc>
