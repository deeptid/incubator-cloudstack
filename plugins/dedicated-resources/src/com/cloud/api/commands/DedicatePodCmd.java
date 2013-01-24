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

import org.apache.cloudstack.api.*;
import org.apache.log4j.Logger;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PodResponse;

import com.cloud.dc.Pod;
import com.cloud.dedicated.dao.DedicatedResourceVO;
import com.cloud.dedicated.services.DedicatedResources;
import com.cloud.dedicated.services.DedicatedService;
import com.cloud.org.Cluster;
import com.cloud.user.Account;
import com.cloud.api.response.*;

@APICommand(name = "dedicatePod", description="Dedicates a Pod.", responseObject=PodResponse.class)
public class DedicatePodCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(DedicatePodCmd.class.getName());

    private static final String s_name = "dedicatepodresponse";
    public static DedicatedService _dedicatedservice;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.ID, type=CommandType.UUID, entityType=PodResponse.class,
            required=true, description="the ID of the Pod")
    private Long id;

    @Parameter(name=ApiConstants.NAME, type=CommandType.STRING, description="the name of the Pod")
    private String podName;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class, description="the ID of the containing domain, null for public pods")
    private Long domainId;
    
    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class, description="the ID of the containing account, null for public pods or domain specific pods")
    private Long accountId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////


    public Long getId() {
        return id;
    }

    public String getPodName() {
        return podName;
    }
    
    public Long getDomainId() {
    	return domainId;
    }

    public Long getAccountId() {
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
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute(){
    	List<? extends DedicatedResources> result = _dedicatedservice.dedicatePod(getId(), getDomainId(), getAccountId());
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
