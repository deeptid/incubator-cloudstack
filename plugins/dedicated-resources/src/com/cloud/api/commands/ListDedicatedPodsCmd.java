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

import org.apache.cloudstack.api.BaseCmd.CommandType;
import org.apache.cloudstack.api.command.user.offering.ListServiceOfferingsCmd;
import org.apache.cloudstack.api.command.user.zone.ListZonesByCmd;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.log4j.Logger;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ZoneResponse;

import com.cloud.api.response.DedicatePodResponse;
import com.cloud.dc.DataCenter;
import com.cloud.dedicated.services.DedicatedResources;
import com.cloud.dedicated.services.DedicatedService;
import com.cloud.org.Cluster;
import com.cloud.utils.Pair;

@APICommand(name = "listDedicatedPods", description="Lists dedicated pods.", responseObject=ClusterResponse.class)
public class ListDedicatedPodsCmd extends BaseListCmd {
	public static final Logger s_logger = Logger.getLogger(ListDedicatedPodsCmd.class.getName());

    private static final String s_name = "listdedicatedpodsresponse";
    public static DedicatedService _dedicatedservice;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class,
            description="the ID of the domain associated with the zone")
    private Long domainId;

    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class,
            description="the ID of the domain associated with the zone")
    private Long accountId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////


    public Long getDomainId(){
        return domainId;
    }

    public Long getAccountId(){
        return accountId;
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
    	List<? extends DedicatedResources> result = _dedicatedservice.listDedicatedPods(this);
    	ListResponse<DedicatePodResponse> response = new ListResponse<DedicatePodResponse>();
    	List<DedicatePodResponse> Responses = new ArrayList<DedicatePodResponse>();
        if (result != null) {
        	 for (DedicatedResources resource : result) {
        		 DedicatePodResponse podresponse = _dedicatedservice.createDedicatePodResponse(resource);
        		 Responses.add(podresponse);
        	 }
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update pod");
        }
    }
}
