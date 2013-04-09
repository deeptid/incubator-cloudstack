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
package com.cloud.manager;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.api.commands.DedicateClusterCmd;
import com.cloud.api.commands.DedicateHostCmd;
import com.cloud.api.commands.DedicatePodCmd;
import com.cloud.api.commands.ListDedicatedClustersCmd;
import com.cloud.api.commands.ListDedicatedHostsCmd;
import com.cloud.api.commands.ListDedicatedPodsCmd;
import com.cloud.api.commands.ListDedicatedZonesCmd;
import com.cloud.api.commands.ReleaseDedicatedClusterCmd;
import com.cloud.api.commands.ReleaseDedicatedHostCmd;
import com.cloud.api.commands.ReleaseDedicatedPodCmd;
import com.cloud.api.response.DedicateClusterResponse;
import com.cloud.api.response.DedicateHostResponse;
import com.cloud.api.response.DedicatePodResponse;
import com.cloud.api.response.DedicateZoneResponse;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.DedicatedResources;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.org.Grouping.AllocationState;
import com.cloud.services.DedicatedService;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostTagsDao;
import com.cloud.user.AccountVO;
import com.cloud.user.UserContext;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;

@Component
@Local({DedicatedService.class })
public class DedicatedResourceManagerImpl implements DedicatedService {
    private static final Logger s_logger = Logger.getLogger(DedicatedResourceManagerImpl.class);

    @Inject AccountDao _accountDao; 
    @Inject DomainDao _domainDao;
    @Inject DataCenterDao _dcDao;
    @Inject HostPodDao _podDao;
    @Inject ClusterDetailsDao _clusterDetailsDao;
    @Inject ClusterDao _clusterDao;
    @Inject CapacityDao _capacityDao;
    @Inject HostDao _hostDao;
    @Inject ConfigurationDao _configDao;
    @Inject HostTagsDao _hostTagsDao;
    @Inject DedicatedResourceDao _dedicatedDao;
    @Inject DataCenterDao _zoneDao;

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE, eventDescription = "dedicating resource")
    public List<DedicatedResourceVO> dedicateResource(Long zoneId, Long podId, Long clusterId, Long hostId, Long domainId, Long accountId, Boolean implicit) {
        // verify parameters
        Long userId = UserContext.current().getCallerUserId();
        
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (implicit == null) {
            implicit = false;
        }
        if (implicit && domainId !=null && accountId != null) {
            throw new InvalidParameterValueException("Please specify 'domain id or account id' OR 'implicit dedication flag' but not both");
        }
        if (!implicit) {
            if (domain == null) {
                throw new InvalidParameterValueException("Unable to find the domain by id " + domainId + ", please specify valid domainId");
            } else {
                if (accountId != null  && account == null) {
                    throw new InvalidParameterValueException("Unable to find the account by id " + accountId);
                }
            }

        }
        //check if account belongs to the domain id
        if (accountId != null) {
            if (domainId == null || account == null || domainId != account.getDomainId()){
                throw new CloudRuntimeException("Please specify the domain id of the account: " + account.getAccountName());
            }
        }
        if (zoneId != null) {
            DataCenterVO dc = _zoneDao.findById(zoneId);
            if (dc == null) {
                throw new InvalidParameterValueException("Unable to find zone by id " + zoneId);
            } else {
                // check if zone is in disabled state
                if (dc.getAllocationState() == AllocationState.Disabled) {
                    throw new InvalidParameterValueException("Zone " + zoneId + " is disabled");
                }
                DedicatedResourceVO dedicatedZone = _dedicatedDao.findByZoneId(zoneId);
                //check if zone is dedicated
                if(dedicatedZone != null) {
                    throw new CloudRuntimeException("Zone is already dedicated");
                }
            }
        }
        if (podId != null){
            HostPodVO pod = _podDao.findById(podId);
            if (pod == null) {
                throw new InvalidParameterValueException("Unable to find pod by id " + podId);
            } else {
                // check if pod is in disabled state
                if (pod.getAllocationState() == AllocationState.Disabled) {
                    throw new InvalidParameterValueException("Pod " + podId + " is disabled");
                }
                DedicatedResourceVO dedicatedPod = _dedicatedDao.findByPodId(podId);
                DedicatedResourceVO dedicatedZoneOfPod = _dedicatedDao.findByZoneId(pod.getDataCenterId());
                //check if pod is dedicated
                if(dedicatedPod != null && dedicatedZoneOfPod != null) {
                    throw new CloudRuntimeException("Pod is already dedicated");
                }
            }
        }
        if (podId == null && hostId == null){
            ClusterVO cluster = _clusterDao.findById(clusterId);
            if (cluster == null) {
                throw new InvalidParameterValueException("Unable to find cluster by id " + clusterId);
            } else {
                //check if cluster is in disabled state
                if (cluster.getAllocationState() == AllocationState.Disabled) {
                    throw new CloudRuntimeException("Cluster " + clusterId + " is not enabled");
                }
                DedicatedResourceVO dedicatedCluster = _dedicatedDao.findByClusterId(clusterId);
                DedicatedResourceVO dedicatedPodOfCluster = _dedicatedDao.findByPodId(cluster.getPodId());
                //check if cluster is dedicated 
                if(dedicatedCluster != null && dedicatedPodOfCluster != null) {
                    throw new CloudRuntimeException("Cluster or its pod is already dedicated");
                }
            }
        }
        if (podId == null && clusterId == null){
            HostVO host = _hostDao.findById(hostId);
            if (host == null) {
                throw new InvalidParameterValueException("Unable to find host by id " + hostId);
            } else {
                //check if host is in disabled state
                if (host.getStatus() != Status.Up) {
                    throw new CloudRuntimeException("Host " + hostId + " is not in Up state");
                }
                //check if host is of routing type
                if (host.getType() != Host.Type.Routing) {
                    throw new CloudRuntimeException("Host " + hostId + " is not of Routing Type");
                }
                DedicatedResourceVO dedicatedHost = _dedicatedDao.findByHostId(hostId);
                DedicatedResourceVO dedicatedClusterOfHost = _dedicatedDao.findByClusterId(host.getClusterId());
                DedicatedResourceVO dedicatedPodOfHost = _dedicatedDao.findByPodId(host.getPodId());
                if(dedicatedHost != null && dedicatedClusterOfHost != null && dedicatedPodOfHost != null) {
                    throw new CloudRuntimeException("Host or its pod/cluster is already dedicated");
                }
            }
        }

        Transaction txn = Transaction.currentTxn();
        txn.start();
        DedicatedResourceVO dedicatedResource = new DedicatedResourceVO(zoneId, podId, clusterId, hostId, null, null, false);
        try {
            if (domainId != null) {
                dedicatedResource.setDomainId(domainId);
                //check if account belongs to the domain id
                if (accountId != null) {
                    dedicatedResource.setAccountId(accountId);
                }
            } else {
                dedicatedResource.setImplicitDedication(implicit);
            }
            dedicatedResource = _dedicatedDao.persist(dedicatedResource);
        } catch (Exception e) {
            s_logger.error("Unable to dedicate resource due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate resource. Please contact Cloud Support.");
        }
        txn.commit();

        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedResource);
        return result;
    }

    @Override
    public DedicateZoneResponse createDedicateZoneResponse(DedicatedResources resource) {
        DedicateZoneResponse dedicateZoneResponse = new DedicateZoneResponse();
        DataCenterVO dc = _dcDao.findById(resource.getDataCenterId());
        DomainVO domain = _domainDao.findById(resource.getDomainId());
        AccountVO account = _accountDao.findById(resource.getAccountId());
        dedicateZoneResponse.setId(resource.getUuid());
        dedicateZoneResponse.setZoneId(dc.getUuid());
        dedicateZoneResponse.setDomainId(domain.getUuid());
        dedicateZoneResponse.setAccountId(account.getUuid());
        dedicateZoneResponse.setObjectName("dedicated zone");
        return dedicateZoneResponse;
    }

    @Override
    public DedicatePodResponse createDedicatePodResponse(DedicatedResources resource) {
        DedicatePodResponse dedicatePodResponse = new DedicatePodResponse();
        HostPodVO pod = _podDao.findById(resource.getPodId());
        DomainVO domain = _domainDao.findById(resource.getDomainId());
        AccountVO account = _accountDao.findById(resource.getAccountId());
        dedicatePodResponse.setId(resource.getUuid());
        dedicatePodResponse.setPodId(pod.getUuid());
        dedicatePodResponse.setDomainId(domain.getUuid());
        dedicatePodResponse.setAccountId(account.getUuid());
        dedicatePodResponse.setObjectName("dedicated pod");
        return dedicatePodResponse;
    }

    @Override
    public DedicateClusterResponse createDedicateClusterResponse(DedicatedResources resource) {
        DedicateClusterResponse dedicateClusterResponse = new DedicateClusterResponse();
        ClusterVO cluster = _clusterDao.findById(resource.getClusterId());
        DomainVO domain = _domainDao.findById(resource.getDomainId());
        AccountVO account = _accountDao.findById(resource.getAccountId());
        dedicateClusterResponse.setId(resource.getUuid());
        dedicateClusterResponse.setClusterId(cluster.getUuid());
        dedicateClusterResponse.setDomainId(domain.getUuid());
        dedicateClusterResponse.setAccountId(account.getUuid());
        dedicateClusterResponse.setObjectName("dedicated cluster");
        return dedicateClusterResponse;
    }

    @Override
    public DedicateHostResponse createDedicateHostResponse(DedicatedResources resource) {
        DedicateHostResponse dedicateHostResponse = new DedicateHostResponse();
        HostVO host = _hostDao.findById(resource.getHostId());
        DomainVO domain = _domainDao.findById(resource.getDomainId());
        AccountVO account = _accountDao.findById(resource.getAccountId());
        dedicateHostResponse.setId(resource.getUuid());
        dedicateHostResponse.setHostId(host.getUuid());
        dedicateHostResponse.setDomainId(domain.getUuid());
        dedicateHostResponse.setAccountId(account.getUuid());
        dedicateHostResponse.setObjectName("dedicated host");
        return dedicateHostResponse;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(DedicatePodCmd.class);
        cmdList.add(DedicateClusterCmd.class);
        cmdList.add(DedicateHostCmd.class);
        cmdList.add(ListDedicatedPodsCmd.class);
        cmdList.add(ListDedicatedClustersCmd.class);
        cmdList.add(ListDedicatedHostsCmd.class);
        cmdList.add(ReleaseDedicatedClusterCmd.class);
        cmdList.add(ReleaseDedicatedHostCmd.class);
        cmdList.add(ReleaseDedicatedPodCmd.class);
        return cmdList;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedZones(ListDedicatedZonesCmd cmd) {
        List<DedicatedResourceVO> zones = new ArrayList<DedicatedResourceVO>();
        Long zoneId = cmd.getZoneId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        Boolean implicit = cmd.getImplicitDedication();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (implicit == null) {
            implicit = false;
        }
        if (zoneId != null) {
            DedicatedResourceVO zone = _dedicatedDao.findByZoneId(zoneId);
            if (zone != null){
                zones.add(zone);
            } else {
                throw new CloudRuntimeException("Zone with Id " + zoneId + "is not dedicated");
            }
        }
        if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list pods associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                zones = _dedicatedDao.findZonesByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null) {
            zones = _dedicatedDao.findZonesByDomainId(domainId);
        }
        return zones;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedPods(ListDedicatedPodsCmd cmd) {
        List<DedicatedResourceVO> pods = new ArrayList<DedicatedResourceVO>();
        Long podId = cmd.getPodId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        Boolean implicit = cmd.getImplicitDedication();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (implicit == null) {
            implicit = false;
        }
        if (podId != null) {
            DedicatedResourceVO pod = _dedicatedDao.findByPodId(podId);
            if (pod != null){
                pods.add(pod);
            } else {
                throw new CloudRuntimeException("Pod with Id " + podId + "is not dedicated");
            }
        }
        if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list pods associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                pods = _dedicatedDao.findPodsByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null) {
            pods = _dedicatedDao.findPodsByDomainId(domainId);
        }
        if (implicit) {
            pods = _dedicatedDao.findPodsByImplicitDedication(implicit);
        }
        return pods;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedClusters(ListDedicatedClustersCmd cmd) {
        List<DedicatedResourceVO> clusters = new ArrayList<DedicatedResourceVO>();
        Long clusterId = cmd.getClusterId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        Boolean implicit = cmd.getImplicitDedication();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (implicit == null) {
            implicit = false;
        }
        if (clusterId != null) {
            DedicatedResourceVO cluster = _dedicatedDao.findByClusterId(clusterId);
            if (cluster != null){
                clusters.add(cluster);
            }
        }
        if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list clusters associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                clusters = _dedicatedDao.findClustersByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null) {
            clusters = _dedicatedDao.findClustersByDomainId(domainId);
        }
        if (implicit) {
            clusters = _dedicatedDao.findClustersByImplicitDedication(implicit);
        }
        return clusters;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedHosts(ListDedicatedHostsCmd cmd) {
        List<DedicatedResourceVO> hosts = new ArrayList<DedicatedResourceVO>();
        Long hostId = cmd.getHostId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        Boolean implicit = cmd.getImplicitDedication();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (implicit == null) {
            implicit = false;
        }
        if (hostId != null) {
            DedicatedResourceVO host = _dedicatedDao.findByHostId(hostId);
            if (host != null){
                hosts.add(host);
            }
        }
        if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list pods associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                hosts = _dedicatedDao.findHostsByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null) {
            hosts = _dedicatedDao.findHostsByDomainId(domainId);
        }
        if (implicit) {
            hosts = _dedicatedDao.findHostsByImplicitDedication(implicit);
        }
        return hosts;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE_RELEASE, eventDescription = "deleting dedicated resource")
    public boolean releaseDedicatedResource(Long zoneId, Long podId, Long clusterId, Long hostId) {
        DedicatedResourceVO resource = new DedicatedResourceVO();
        Long resourceId = null;
        if (zoneId != null) {
            resource = _dedicatedDao.findByZoneId(zoneId);
        }
        if (podId != null) {
            resource = _dedicatedDao.findByPodId(podId);
        }
        if (clusterId != null) {
            resource = _dedicatedDao.findByClusterId(clusterId);
        }
        if (hostId != null ) {
            resource = _dedicatedDao.findByHostId(hostId);
        }
        Transaction txn = Transaction.currentTxn();
        txn.start();
        if (resource != null) {
            resourceId = resource.getId();
            if (!_dedicatedDao.remove(resourceId)) {
                throw new CloudRuntimeException("Failed to delete Resource " + resourceId);
            }
        }else {
            throw new CloudRuntimeException("No Dedicated Resource available to release");
        }
        txn.commit();

        return true;
    }
}
