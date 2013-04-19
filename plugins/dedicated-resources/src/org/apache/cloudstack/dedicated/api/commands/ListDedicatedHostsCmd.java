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
package org.apache.cloudstack.dedicated.api.commands;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.dedicated.api.response.DedicateHostResponse;
import org.apache.cloudstack.dedicated.services.DedicatedService;
import org.apache.log4j.Logger;

import com.cloud.dc.DedicatedResources;

@APICommand(name = "listDedicatedHosts", description = "Lists dedicated hosts.", responseObject = DedicateHostResponse.class)
public class ListDedicatedHostsCmd extends BaseListCmd {
	public static final Logger s_logger = Logger.getLogger(ListDedicatedHostsCmd.class.getName());

    private static final String s_name = "listdedicatedhostsresponse";
    @Inject DedicatedService dedicatedService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name=ApiConstants.HOST_ID, type=CommandType.UUID, entityType=HostResponse.class,
            description="the ID of the host")
    private Long hostId;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class,
            description="the ID of the domain associated with the host")
    private Long domainId;

    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class,
            description="the ID of the domain associated with the host")
    private Long accountId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getHostId() {
        return hostId;
    }

    public Long getDomainId(){
        return domainId;
    }

    public Long getAccountId(){
        return accountId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////l
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute(){
    	List<? extends DedicatedResources> result = dedicatedService.listDedicatedHosts(this);
    	ListResponse<DedicateHostResponse> response = new ListResponse<DedicateHostResponse>();
    	List<DedicateHostResponse> Responses = new ArrayList<DedicateHostResponse>();
        if (result != null) {
        	 for (DedicatedResources resource : result) {
        		 DedicateHostResponse hostResponse = dedicatedService.createDedicateHostResponse(resource);
        		 Responses.add(hostResponse);
        	 }
        	 response.setResponses(Responses);
             response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to list dedicated hosts");
        }
    }
}
