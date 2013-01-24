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

import javax.inject.Inject;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.log4j.Logger;

import com.cloud.api.response.DedicatePodResponse;
import com.cloud.dc.DedicatedResources;

import com.cloud.services.DedicatedService;
import com.cloud.user.Account;

@APICommand(name = "dedicatePod", description="Dedicates a Pod.", responseObject=DedicatePodResponse.class)
public class DedicatePodCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(DedicatePodCmd.class.getName());

    private static final String s_name = "dedicatepodresponse";
    @Inject public DedicatedService _dedicatedservice;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.POD_ID, type=CommandType.UUID, entityType=PodResponse.class,
            required=true, description="the ID of the Pod")
    private Long podId;

    @Parameter(name=ApiConstants.NAME, type=CommandType.STRING, description="the name of the Pod")
    private String podName;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class, description="the ID of the containing domain, null for public pods")
    private Long domainId;

    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class, description="the ID of the containing account, null for public pods or domain specific pods")
    private Long accountId;

    @Parameter(name=ApiConstants.IMPLICIT_DEDICATION, type=CommandType.BOOLEAN, description="if true, dedicated resources will be used")
    private Boolean implicitDedication;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////


    public Long getPodId() {
        return podId;
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

    public Boolean getImplcitDedication() {
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
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public void execute(){
        List<? extends DedicatedResources> result = _dedicatedservice.dedicateResource(null, getPodId(), null, null, getDomainId(), getAccountId(), getImplcitDedication());
        ListResponse<DedicatePodResponse> response = new ListResponse<DedicatePodResponse>();
        List<DedicatePodResponse> podResponseList = new ArrayList<DedicatePodResponse>();
        if (result != null) {
            for (DedicatedResources resource : result) {
                DedicatePodResponse podresponse = _dedicatedservice.createDedicatePodResponse(resource);
                podResponseList.add(podresponse);
            }
            response.setResponses(podResponseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to dedicate pod");
        }
    }
}
