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
package com.cloud.api.response;

import java.util.List;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dc.Pod;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.BaseResponse;

public class DedicatePodResponse extends BaseResponse {
    @SerializedName("podid") @Param(description="the ID of the Pod")
    private Long podId;
    
    @SerializedName("domainid") @Param(description="the domain ID of the Pod")
    private Long domainId;
    
    @SerializedName("accountid") @Param(description="the Account ID of the Pod")
    private Long accountId;


    public Long getPodId() {
        return podId;
    }

    public void setPodId(Long podId) {
        this.podId = podId;
    }
    
    public Long getDomainId() {
    	return domainId;
    }
    
    public void setDomainId(Long domainId) {
        this.domainId = domainId;    
    }
    
    public Long getAccountId() {
    	return accountId;
    }
    
    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
