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

import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dc.DedicatedResources;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DedicatedResources.class)
public class DedicatePodResponse extends BaseResponse {
    @SerializedName("id") @Param(description="the ID of the dedicated resource")
    private String id;

    @SerializedName("podid") @Param(description="the ID of the Pod")
    private Long podId;

    @SerializedName("domainid") @Param(description="the domain ID to which the Pod is dedicated")
    private Long domainId;

    @SerializedName("accountid") @Param(description="the Account Id to which the Pod is dedicated")
    private Long accountId;

    @SerializedName("implicitDedication") @Param(description="the Account ID of the Pod")
    private Boolean implicitDedication;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public boolean getImplicitDedication() {
        return implicitDedication;
    }

    public void setImplicitDedication(boolean implicitDedication) {
        this.implicitDedication = implicitDedication;
    }
}
