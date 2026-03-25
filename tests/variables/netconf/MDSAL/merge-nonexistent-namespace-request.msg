<rpc message-id="2" a="64" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
  <edit-config>
    <target>
      <candidate/>
    </target>
    <default-operation>merge</default-operation>
    <config>
      <does-not-exist xmlns="urn:this:is:in:a:nonexistent:namespace">
        <entry>
          <id>TOY001</id>
          <name>Some Toy</name>
          <year>2016</year>
          <category>toys</category>
        </entry>
      </does-not-exist>
    </config>
  </edit-config>
</rpc>
