<rpc message-id="2" a="64" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
<edit-config>
<target>
<candidate/>
</target>
<default-operation>merge</default-operation>
<config>
<modules xmlns="urn:opendaylight:params:xml:ns:yang:controller:config">
<module>
<type xmlns:prefix="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl">prefix:threadfactory-naming</type>
<name>name0</name>
<name-prefix xmlns="urn:opendaylight:params:xml:ns:yang:controller:threadpool:impl">remote-connector-processing-executor</name-prefix>
</module>
</modules>
</config>
</edit-config>
</rpc>
