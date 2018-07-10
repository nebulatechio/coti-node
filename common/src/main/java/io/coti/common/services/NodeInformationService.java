package io.coti.common.services;

import io.coti.common.http.NodeInformationResponse;
import io.coti.common.services.interfaces.INodeInformationService;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
public class NodeInformationService implements INodeInformationService {

    public NodeInformationResponse getNodeInformation() {
        NodeInformationResponse response = new NodeInformationResponse();
        try {
            response.setIpV4Address(InetAddress.getLocalHost());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return response;
    }
}