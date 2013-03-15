// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.api.commands;

import java.util.ArrayList;
import java.util.List;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.log4j.Logger;

import com.cloud.api.response.DedicateClusterResponse;
import com.cloud.dc.DedicatedResources;
import com.cloud.services.DedicatedService;

@APICommand(name = "listDedicatedPods", description="Lists dedicated pods.", responseObject=ClusterResponse.class)
public class ListDedicatedClustersCmd extends BaseListCmd {
	public static final Logger s_logger = Logger.getLogger(ListDedicatedPodsCmd.class.getName());

    private static final String s_name = "listdedicatedpodsresponse";
    public static DedicatedService _dedicatedservice;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name=ApiConstants.CLUSTER_ID, type=CommandType.UUID, entityType=ClusterResponse.class,
            description="the ID of the cluster")
    private Long clusterId;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class,
            description="the ID of the domain associated with the cluster")
    private Long domainId;

    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class,
            description="the ID of the domain associated with the cluster")
    private Long accountId;

    @Parameter(name=ApiConstants.IMPLICIT_DEDICATION, type=CommandType.BOOLEAN, description="if true, dedicated resources will be used")
    private Boolean implicitDedication;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getClusterId() {
        return clusterId;
    }

    public Long getDomainId(){
        return domainId;
    }

    public Long getAccountId(){
        return accountId;
    }

    public Boolean getImplicitDedication() {
        return implicitDedication;
    }
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute(){
        List<? extends DedicatedResources> result = _dedicatedservice.listDedicatedClusters(this);
        ListResponse<DedicateClusterResponse> response = new ListResponse<DedicateClusterResponse>();
        List<DedicateClusterResponse> Responses = new ArrayList<DedicateClusterResponse>();
        if (result != null) {
            for (DedicatedResources resource : result) {
                DedicateClusterResponse clusterResponse = _dedicatedservice.createDedicateClusterResponse(resource);
                Responses.add(clusterResponse);
            }
            response.setResponses(Responses);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update pod");
        }
    }
}
