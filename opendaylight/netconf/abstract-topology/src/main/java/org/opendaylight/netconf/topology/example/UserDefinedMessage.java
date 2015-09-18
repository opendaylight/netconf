package org.opendaylight.netconf.topology.example;

import java.io.Serializable;

public interface UserDefinedMessage extends Serializable {

    class YangDefinedMessage implements UserDefinedMessage {

        // Normalized Node based

    }

    class YangSubtreeMessage extends YangDefinedMessage {

        // Data +

    }
}
