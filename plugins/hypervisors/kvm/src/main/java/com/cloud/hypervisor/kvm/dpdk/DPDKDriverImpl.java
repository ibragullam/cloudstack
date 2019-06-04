/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.hypervisor.kvm.dpdk;

import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.script.Script;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.Map;

public class DPDKDriverImpl extends AdapterBase implements DPDKDriver {
    static final String DPDK_PORT_PREFIX = "csdpdk-";

    private final String dpdkPortVhostUserType = "dpdkvhostuser";
    private final String dpdkPortVhostUserClientType = "dpdkvhostuserclient";

    private static final Logger s_logger = Logger.getLogger(DPDKDriver.class);

    public DPDKDriverImpl() {
    }

    /**
     * Get the next DPDK port name to be created
     */
    public String getNextDpdkPort() {
        int portNumber = getDpdkLatestPortNumberUsed();
        return DPDK_PORT_PREFIX + String.valueOf(portNumber + 1);
    }

    /**
     * Get the latest DPDK port number created on a DPDK enabled host
     */
    public int getDpdkLatestPortNumberUsed() {
        s_logger.debug("Checking the last DPDK port created");
        String cmd = "ovs-vsctl show | grep Port | grep " + DPDK_PORT_PREFIX + " | " +
                "awk '{ print $2 }' | sort -rV | head -1";
        String port = Script.runSimpleBashScript(cmd);
        int portNumber = 0;
        if (StringUtils.isNotBlank(port)) {
            String unquotedPort = port.replace("\"", "");
            String dpdkPortNumber = unquotedPort.split(DPDK_PORT_PREFIX)[1];
            portNumber = Integer.valueOf(dpdkPortNumber);
        }
        return portNumber;
    }

    /**
     * Add OVS port (if it does not exist) to bridge with DPDK support
     */
    public void addDpdkPort(String bridgeName, String port, String vlan, DPDKHelper.VHostUserMode vHostUserMode, String dpdkOvsPath) {
        String type = vHostUserMode == DPDKHelper.VHostUserMode.SERVER ?
                dpdkPortVhostUserType :
                dpdkPortVhostUserClientType;

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("ovs-vsctl add-port %s %s " +
                "vlan_mode=access tag=%s " +
                "-- set Interface %s type=%s", bridgeName, port, vlan, port, type));

        if (vHostUserMode == DPDKHelper.VHostUserMode.CLIENT) {
            stringBuilder.append(String.format(" options:vhost-server-path=%s/%s",
                    dpdkOvsPath, port));
        }

        String cmd = stringBuilder.toString();
        s_logger.debug("DPDK property enabled, executing: " + cmd);
        Script.runSimpleBashScript(cmd);
    }

    /**
     * Since DPDK user client/server mode, retrieve the guest interfaces mode from the DPDK vHost User mode
     */
    public String getGuestInterfacesModeFromDPDKVhostUserMode(DPDKHelper.VHostUserMode dpdKvHostUserMode) {
        return dpdKvHostUserMode == DPDKHelper.VHostUserMode.CLIENT ? "server" : "client";
    }

    /**
     * Get DPDK vHost User mode from extra config. If it is not present, server is returned as default
     */
    public DPDKHelper.VHostUserMode getDPDKvHostUserMode(Map<String, String> extraConfig) {
        return extraConfig.containsKey(DPDKHelper.DPDK_VHOST_USER_MODE) ?
                DPDKHelper.VHostUserMode.fromValue(extraConfig.get(DPDKHelper.DPDK_VHOST_USER_MODE)) :
                DPDKHelper.VHostUserMode.SERVER;
    }

    /**
     * Check for additional extra 'dpdk-interface' configurations, return them appended
     */
    public String getExtraDpdkProperties(Map<String, String> extraConfig) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String key : extraConfig.keySet()) {
            if (key.startsWith(DPDKHelper.DPDK_INTERFACE_PREFIX)) {
                stringBuilder.append(extraConfig.get(key));
            }
        }
        return stringBuilder.toString();
    }
}