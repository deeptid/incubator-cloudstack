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
package com.cloud.dedicated.services;

import java.util.List;

import com.cloud.api.commands.ListDedicatedPodsCmd;
import com.cloud.api.response.DedicateClusterResponse;
import com.cloud.api.response.DedicateHostResponse;
import com.cloud.api.response.DedicatePodResponse;
import com.cloud.dedicated.dao.DedicatedResourceVO;
import com.cloud.host.Host;
import com.cloud.org.Cluster;
import com.cloud.utils.component.PluggableService;


public interface DedicatedService extends PluggableService{
	/**
     * Dedicates a pod. Will not allow you to dedicate pods to any domain or account.
     *
     * @param DedicatePodCmd
     *            api command
     */
	List<? extends DedicatedResources> dedicatePod(Long id, Long domainId, Long accountId);
	
	/**
     * Dedicates a Cluster. Will not allow you to dedicate pods to any domain or account.
     *
     * @param DedicateClusterCmd
     *            api command
     */
	List<? extends DedicatedResources> dedicateCluster(Long clusterId, Long domainId, Long accountId);

	/**
     * Dedicates a host. Will not allow you to dedicate hosts to any domain or account.
     *
     * @param DedicateHostCmd
     *            api command
     */
	List<? extends DedicatedResources> dedicateHost(Long id, Long domainId, Long accountId);

	DedicatePodResponse createDedicatePodResponse(DedicatedResources resource);

	DedicateClusterResponse createDedicateClusterResponse(
			DedicatedResources resource);

	DedicateHostResponse createDedicateHostResponse(DedicatedResources resource);

	List<DedicatedResourceVO> listDedicatedPods(ListDedicatedPodsCmd cmd);
    
}
