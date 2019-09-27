package org.opendaylight.netconf.nettyutil;

import static com.google.common.base.Verify.verify;

import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;

public class NetconfClientBuilder extends ClientBuilder {
    @Override
    protected ClientBuilder fillWithDefaultValues() {
        final boolean needFactory = factory == null;

        super.fillWithDefaultValues();
        if (needFactory) {
            factory = NetconfSshClient.DEFAULT_NETCONF_SSH_CLIENT_FACTORY;
        }
        return this;
    }

    @Override
    public NetconfSshClient build() {
        final SshClient client = super.build();
        verify(client instanceof NetconfSshClient, "Unexpected client %s", client);
        return (NetconfSshClient) client;
    }
}
