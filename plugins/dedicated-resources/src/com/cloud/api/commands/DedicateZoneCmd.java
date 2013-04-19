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
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.log4j.Logger;

import com.cloud.api.response.DedicateZoneResponse;
import com.cloud.dc.DedicatedResources;
import com.cloud.event.EventTypes;
import com.cloud.services.DedicatedService;
import com.cloud.user.Account;

@APICommand(name = "dedicateZone", description ="Dedicates a zones.", responseObject = DedicateZoneResponse.class)
public class DedicateZoneCmd extends BaseAsyncCmd {
    public static final Logger s_logger = Logger.getLogger(DedicateZoneCmd.class.getName());

    private static final String s_name = "dedicatezoneresponse";
    @Inject public DedicatedService dedicatedService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.ZONE_ID, type=CommandType.UUID, entityType=ZoneResponse.class,
            required=true, description="the ID of the zone")
    private Long zoneId;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType=DomainResponse.class, required=true, description="the ID of the containing domain")
    private Long domainId;

    @Parameter(name=ApiConstants.ACCOUNT_ID, type=CommandType.UUID, entityType=AccountResponse.class, description="the ID of the containing account, null for public zone or domain specific zone")
    private Long accountId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////


    public Long getZoneId() {
        return zoneId;
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
        List<? extends DedicatedResources> result = dedicatedService.dedicateZone(getZoneId(), getDomainId(), getAccountId());
        ListResponse<DedicateZoneResponse> response = new ListResponse<DedicateZoneResponse>();
        List<DedicateZoneResponse> zoneResponseList = new ArrayList<DedicateZoneResponse>();
        if (result != null) {
            for (DedicatedResources resource : result) {
                DedicateZoneResponse zoneresponse = dedicatedService.createDedicateZoneResponse(resource);
                zoneResponseList.add(zoneresponse);
            }
            response.setResponses(zoneResponseList);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to dedicate zone");
        }
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_DEDICATE_RESOURCE;
    }

    @Override
    public String getEventDescription() {
        return "dedicating a zone";
    }
}
