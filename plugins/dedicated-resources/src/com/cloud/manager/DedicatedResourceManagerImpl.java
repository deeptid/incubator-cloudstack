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
import com.cloud.api.commands.DedicateZoneCmd;
import com.cloud.api.commands.ListDedicatedClustersCmd;
import com.cloud.api.commands.ListDedicatedHostsCmd;
import com.cloud.api.commands.ListDedicatedPodsCmd;
import com.cloud.api.commands.ListDedicatedZonesCmd;
import com.cloud.api.commands.ReleaseDedicatedClusterCmd;
import com.cloud.api.commands.ReleaseDedicatedHostCmd;
import com.cloud.api.commands.ReleaseDedicatedPodCmd;
import com.cloud.api.commands.ReleaseDedicatedZoneCmd;
import com.cloud.api.response.DedicateClusterResponse;
import com.cloud.api.response.DedicateHostResponse;
import com.cloud.api.response.DedicatePodResponse;
import com.cloud.api.response.DedicateZoneResponse;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.DedicatedResources;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.org.Grouping.AllocationState;
import com.cloud.services.DedicatedService;
import com.cloud.user.AccountVO;
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
    @Inject HostPodDao _podDao;
    @Inject ClusterDao _clusterDao;
    @Inject HostDao _hostDao;
    @Inject DedicatedResourceDao _dedicatedDao;
    @Inject DataCenterDao _zoneDao;

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE, eventDescription = "dedicating a Zone")
    public List<DedicatedResourceVO> dedicateZone(Long zoneId, Long domainId, Long accountId) {
        checkAccountAndDomain(accountId, domainId);
        DataCenterVO dc = _zoneDao.findById(zoneId);
        if (dc == null) {
            throw new InvalidParameterValueException("Unable to find zone by id " + zoneId);
        } else {
            DedicatedResourceVO dedicatedZone = _dedicatedDao.findByZoneId(zoneId);
            //check if zone is dedicated
            if(dedicatedZone != null) {
                s_logger.error("Zone " + dc.getName() + " is already dedicated");
                throw new CloudRuntimeException("Zone  " + dc.getName() + " is already dedicated");
            }
            //check if any resource under this zone is dedicated to different account or sub-domain
            List<HostPodVO> pods = _podDao.listByDataCenterId(dc.getId());
            for (HostPodVO pod : pods) {
                DedicatedResourceVO dPod = _dedicatedDao.findByPodId(pod.getId());
                if (dPod != null && !(getDomainChildIds(domainId).contains(dPod.getDomainId()))) {
                    throw new CloudRuntimeException("Pod " + pod.getName() + " under this Zone " + dc.getName() + " is dedicated to different account/domain");
                }
            }
            List<ClusterVO> clusters = _clusterDao.listClustersByDcId(dc.getId());
            for (ClusterVO cluster : clusters) {
                DedicatedResourceVO dCluster = _dedicatedDao.findByClusterId(cluster.getId());
                if (dCluster != null && !(getDomainChildIds(domainId).contains(dCluster.getDomainId()))) {
                    throw new CloudRuntimeException("Cluster " + cluster.getName() + " under this Zone " + dc.getName() + " is dedicated to different account/domain");
                }
            }
            List<HostVO> hosts = _hostDao.listByDataCenterId(dc.getId());
            for (HostVO host : hosts) {
                DedicatedResourceVO dHost = _dedicatedDao.findByHostId(host.getId());
                if (dHost != null && !(getDomainChildIds(domainId).contains(dHost.getDomainId()))) {
                    throw new CloudRuntimeException("Host " + host.getName() + " under this Zone " + dc.getName() + " is dedicated to different account/domain");
                }
            }
        }
        Transaction txn = Transaction.currentTxn();
        txn.start();
        DedicatedResourceVO dedicatedResource = new DedicatedResourceVO(zoneId, null, null, null, null, null);
        try {
            dedicatedResource.setDomainId(domainId);
            if (accountId != null) {
                dedicatedResource.setAccountId(accountId);
            }
            dedicatedResource = _dedicatedDao.persist(dedicatedResource);
        } catch (Exception e) {
            s_logger.error("Unable to dedicate zone due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate zone. Please contact Cloud Support.");
        }
        txn.commit();

        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedResource);
        return result;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE, eventDescription = "dedicating a Pod")
    public List<DedicatedResourceVO> dedicatePod(Long podId, Long domainId, Long accountId) {
        checkAccountAndDomain(accountId, domainId);
        HostPodVO pod = _podDao.findById(podId);
        if (pod == null) {
            throw new InvalidParameterValueException("Unable to find pod by id " + podId);
        } else {
            DedicatedResourceVO dedicatedPod = _dedicatedDao.findByPodId(podId);
            DedicatedResourceVO dedicatedZoneOfPod = _dedicatedDao.findByZoneId(pod.getDataCenterId());
            //check if pod is dedicated
            if(dedicatedPod != null ) {
                s_logger.error("Pod " + pod.getName() + " is already dedicated");
                throw new CloudRuntimeException("Pod " + pod.getName() + " is already dedicated");
            }
            if (dedicatedZoneOfPod != null) {
                //can dedicate a pod if account/domain belongs to the domain having dedicated zone 
                if ((dedicatedZoneOfPod.getAccountId() == null && !(getDomainChildIds(dedicatedZoneOfPod.getDomainId()).contains(domainId))) 
                        || (dedicatedZoneOfPod.getDomainId() == domainId && accountId == null)) {
                    DataCenterVO zone = _zoneDao.findById(pod.getDataCenterId());
                    s_logger.error("Cannot dedicate Pod. Its zone is already dedicated");
                    throw new CloudRuntimeException("Pod's Zone " + zone.getName() + " is already dedicated");
                }
            }
            //check if any resource under this pod is dedicated to different account or sub-domain
            List<ClusterVO> clusters = _clusterDao.listByPodId(pod.getId());
            for (ClusterVO cluster : clusters) {
                DedicatedResourceVO dCluster = _dedicatedDao.findByClusterId(cluster.getId());
                if (dCluster != null && !(getDomainChildIds(domainId).contains(dCluster.getDomainId()))) {
                    throw new CloudRuntimeException("Cluster " + cluster.getName() + " under this Pod " + pod.getName() + " is dedicated to different account/domain");
                }
            }
            List<HostVO> hosts = _hostDao.findByPodId(pod.getId());
            for (HostVO host : hosts) {
                DedicatedResourceVO dHost = _dedicatedDao.findByHostId(host.getId());
                if (dHost != null && !(getDomainChildIds(domainId).contains(dHost.getDomainId()))) {
                    throw new CloudRuntimeException("Host " + host.getName() + " under this Pod " + pod.getName() + " is dedicated to different account/domain");
                }
            }
        }
        Transaction txn = Transaction.currentTxn();
        txn.start();
        DedicatedResourceVO dedicatedResource = new DedicatedResourceVO(null, podId, null, null, null, null);
        try {
            dedicatedResource.setDomainId(domainId);
            if (accountId != null) {
                dedicatedResource.setAccountId(accountId);
            }
            dedicatedResource = _dedicatedDao.persist(dedicatedResource);
        } catch (Exception e) {
            s_logger.error("Unable to dedicate pod due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate pod. Please contact Cloud Support.");
        }
        txn.commit();

        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedResource);
        return result;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE, eventDescription = "dedicating a Cluster")
    public List<DedicatedResourceVO> dedicateCluster(Long clusterId, Long domainId, Long accountId) {
        checkAccountAndDomain(accountId, domainId);

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
            DedicatedResourceVO dedicatedZoneOfCluster = _dedicatedDao.findByZoneId(cluster.getDataCenterId());
            //check if cluster is dedicated 
            if(dedicatedCluster != null) {
                s_logger.error("Cluster " + cluster.getName() + " is already dedicated");
                throw new CloudRuntimeException("Cluster "+ cluster.getName() + " is already dedicated");
            }
            if (dedicatedPodOfCluster != null) {
                //can dedicate a cluster if account/domain belongs to the domain having dedicated pod
                if ((dedicatedPodOfCluster.getAccountId() != null && !(getDomainChildIds(dedicatedPodOfCluster.getDomainId()).contains(domainId))) 
                        || (dedicatedPodOfCluster.getDomainId() == domainId && accountId == null)) {
                    s_logger.error("Cannot dedicate Cluster. Its Pod is already dedicated");
                    HostPodVO pod = _podDao.findById(cluster.getPodId());
                    throw new CloudRuntimeException("Cluster's Pod " +  pod.getName() + " is already dedicated");
                }
            }
            if (dedicatedZoneOfCluster != null) {
                //can dedicate a cluster if account/domain belongs to the domain having dedicated zone 
                if ((dedicatedZoneOfCluster.getAccountId() == null && !(getDomainChildIds(dedicatedZoneOfCluster.getDomainId()).contains(domainId))) 
                        || (dedicatedZoneOfCluster.getDomainId() == domainId && accountId == null)) {
                    s_logger.error("Cannot dedicate Cluster. Its zone is already dedicated");
                    DataCenterVO zone = _zoneDao.findById(cluster.getDataCenterId());
                    throw new CloudRuntimeException("Cluster's Zone "+ zone.getName() + " is already dedicated");
                }
            }
            //check if any resource under this pod is dedicated to different account or sub-domain
            List<HostVO> hosts = _hostDao.findByClusterId(cluster.getId());
            for (HostVO host : hosts) {
                DedicatedResourceVO dHost = _dedicatedDao.findByHostId(host.getId());
                if (dHost != null && !(getDomainChildIds(domainId).contains(dHost.getDomainId()))) {
                    throw new CloudRuntimeException("Host " + host.getName() + " under this Cluster " + cluster.getName() + " is dedicated to different account/domain");
                }
            }
        }
        Transaction txn = Transaction.currentTxn();
        txn.start();
        DedicatedResourceVO dedicatedResource = new DedicatedResourceVO(null, null, clusterId, null, null, null);
        try {
            dedicatedResource.setDomainId(domainId);
            if (accountId != null) {
                dedicatedResource.setAccountId(accountId);
            }
            dedicatedResource = _dedicatedDao.persist(dedicatedResource);
        } catch (Exception e) {
            s_logger.error("Unable to dedicate host due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate cluster. Please contact Cloud Support.");
        }
        txn.commit();

        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedResource);
        return result;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE, eventDescription = "dedicating a Host")
    public List<DedicatedResourceVO> dedicateHost(Long hostId, Long domainId, Long accountId) {
        checkAccountAndDomain(accountId, domainId);

        HostVO host = _hostDao.findById(hostId);
        if (host == null) {
            throw new InvalidParameterValueException("Unable to find host by id " + hostId);
        } else {
            //check if host is of routing type
            if (host.getType() != Host.Type.Routing) {
                throw new CloudRuntimeException("Invalid host type for host " + host.getName());
            }
            DedicatedResourceVO dedicatedHost = _dedicatedDao.findByHostId(hostId);
            DedicatedResourceVO dedicatedClusterOfHost = _dedicatedDao.findByClusterId(host.getClusterId());
            DedicatedResourceVO dedicatedPodOfHost = _dedicatedDao.findByPodId(host.getPodId());
            DedicatedResourceVO dedicatedZoneOfHost = _dedicatedDao.findByZoneId(host.getDataCenterId());
            if(dedicatedHost != null) {
                s_logger.error("Host "+  host.getName() + " is already dedicated");
                throw new CloudRuntimeException("Host "+  host.getName() + " is already dedicated");
            }
            if (dedicatedClusterOfHost != null) {
                //can dedicate a host if account/domain belongs to the domain having dedicated cluster
                if ((dedicatedClusterOfHost.getAccountId() == null && !(getDomainChildIds(dedicatedClusterOfHost.getDomainId()).contains(domainId))) 
                        || (dedicatedClusterOfHost.getDomainId() == domainId && accountId == null)) {
                    ClusterVO cluster = _clusterDao.findById(host.getClusterId());
                    s_logger.error("Host's Cluster " + cluster.getName() + " is already dedicated");
                    throw new CloudRuntimeException("Host's Cluster " + cluster.getName() + " is already dedicated"); 
                }
            }
            if (dedicatedPodOfHost != null){
                //can dedicate a host if account/domain belongs to the domain having dedicated pod
                if ((dedicatedPodOfHost.getAccountId() == null && !(getDomainChildIds(dedicatedPodOfHost.getDomainId()).contains(domainId))) 
                        || (dedicatedPodOfHost.getDomainId() == domainId && accountId == null)) {
                    HostPodVO pod = _podDao.findById(host.getPodId());
                    s_logger.error("Host's Pod " + pod.getName() + " is already dedicated");
                    throw new CloudRuntimeException("Host's Pod " + pod.getName() + " is already dedicated"); 
                }
            }
            if (dedicatedZoneOfHost !=  null) {
                //can dedicate a host if account/domain belongs to the domain having dedicated zone
                if ((dedicatedZoneOfHost.getAccountId() == null && !(getDomainChildIds(dedicatedZoneOfHost.getDomainId()).contains(domainId))) 
                        || (dedicatedZoneOfHost.getDomainId() == domainId && accountId == null)) {
                    DataCenterVO zone = _zoneDao.findById(host.getDataCenterId());
                    s_logger.error("Host's Data Center " + zone.getName() + " is already dedicated");
                    throw new CloudRuntimeException("Host's Data Center " + zone.getName() + " is already dedicated"); 
                }
            }
        }
        Transaction txn = Transaction.currentTxn();
        txn.start();
        DedicatedResourceVO dedicatedResource = new DedicatedResourceVO(null, null, null, hostId, null, null);
        try {
            dedicatedResource.setDomainId(domainId);
            if (accountId != null) {
                dedicatedResource.setAccountId(accountId);
            }
            dedicatedResource = _dedicatedDao.persist(dedicatedResource);
        } catch (Exception e) {
            s_logger.error("Unable to dedicate host due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate host. Please contact Cloud Support.");
        }
        txn.commit();

        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedResource);
        return result;
    }

    private void checkAccountAndDomain(Long accountId, Long domainId) {
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (domain == null) {
            throw new InvalidParameterValueException("Unable to find the domain by id " + domainId + ", please specify valid domainId");
        } else {
            if (accountId != null  && account == null) {
                throw new InvalidParameterValueException("Unable to find the account by id " + accountId);
            }
        }
        //check if account belongs to the domain id
        if (accountId != null) {
            if (account == null || domainId != account.getDomainId()){
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        }
    }

    private List<Long> getDomainChildIds(long domainId) {
        DomainVO domainRecord = _domainDao.findById(domainId);
        List<Long> domainIds = new ArrayList<Long>();
        domainIds.add(domainRecord.getId());
        // find all domain Ids till leaf
        List<DomainVO> allChildDomains = _domainDao.findAllChildren(domainRecord.getPath(), domainRecord.getId());
        for (DomainVO domain : allChildDomains) {
            domainIds.add(domain.getId());
        }
        return domainIds;
    }

    @Override
    public DedicateZoneResponse createDedicateZoneResponse(DedicatedResources resource) {
        DedicateZoneResponse dedicateZoneResponse = new DedicateZoneResponse();
        DataCenterVO dc = _zoneDao.findById(resource.getDataCenterId());
        DomainVO domain = _domainDao.findById(resource.getDomainId());
        AccountVO account = _accountDao.findById(resource.getAccountId());
        dedicateZoneResponse.setId(resource.getUuid());
        dedicateZoneResponse.setZoneId(dc.getUuid());
        dedicateZoneResponse.setZoneName(dc.getName());
        dedicateZoneResponse.setDomainId(domain.getUuid());
        if (account != null) {
            dedicateZoneResponse.setAccountId(account.getUuid());
        }
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
        dedicatePodResponse.setPodName(pod.getName());
        dedicatePodResponse.setDomainId(domain.getUuid());
        if (account != null) {
            dedicatePodResponse.setAccountId(account.getUuid());
        }
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
        dedicateClusterResponse.setClusterName(cluster.getName());
        dedicateClusterResponse.setDomainId(domain.getUuid());
        if (account != null) {
            dedicateClusterResponse.setAccountId(account.getUuid());
        }
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
        dedicateHostResponse.setHostName(host.getName());
        dedicateHostResponse.setDomainId(domain.getUuid());
        if (account != null) {
            dedicateHostResponse.setAccountId(account.getUuid());
        }
        dedicateHostResponse.setObjectName("dedicated host");
        return dedicateHostResponse;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(DedicateZoneCmd.class);
        cmdList.add(DedicatePodCmd.class);
        cmdList.add(DedicateClusterCmd.class);
        cmdList.add(DedicateHostCmd.class);
        cmdList.add(ListDedicatedZonesCmd.class);
        cmdList.add(ListDedicatedPodsCmd.class);
        cmdList.add(ListDedicatedClustersCmd.class);
        cmdList.add(ListDedicatedHostsCmd.class);
        cmdList.add(ReleaseDedicatedClusterCmd.class);
        cmdList.add(ReleaseDedicatedHostCmd.class);
        cmdList.add(ReleaseDedicatedPodCmd.class);
        cmdList.add(ReleaseDedicatedZoneCmd.class);
        return cmdList;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedZones(ListDedicatedZonesCmd cmd) {
        List<DedicatedResourceVO> zones = new ArrayList<DedicatedResourceVO>();
        Long zoneId = cmd.getZoneId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (zoneId != null) {
            DedicatedResourceVO zone = _dedicatedDao.findByZoneId(zoneId);
            if (zone != null){
                zones.add(zone);
            } else {
                throw new CloudRuntimeException("Zone with Id " + zoneId + " is not dedicated");
            }
        } else if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list pods associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                zones = _dedicatedDao.findZonesByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null && accountId == null) {
            zones = _dedicatedDao.findZonesByDomainId(domainId);
        } else {
            zones = _dedicatedDao.listZones();
        }
        return zones;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedPods(ListDedicatedPodsCmd cmd) {
        List<DedicatedResourceVO> pods = new ArrayList<DedicatedResourceVO>();
        Long podId = cmd.getPodId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (podId != null) {
            DedicatedResourceVO pod = _dedicatedDao.findByPodId(podId);
            if (pod != null){
                pods.add(pod);
            } else {
                throw new CloudRuntimeException("Pod with Id " + podId + " is not dedicated");
            }
        } else if (accountId != null) {
            // for domainId != null
            // we made the decision to only list pods associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                pods = _dedicatedDao.findPodsByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null && accountId == null) {
            pods = _dedicatedDao.findPodsByDomainId(domainId);
        } else {
            pods = _dedicatedDao.listPods();
        }
        return pods;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedClusters(ListDedicatedClustersCmd cmd) {
        List<DedicatedResourceVO> clusters = new ArrayList<DedicatedResourceVO>();
        Long clusterId = cmd.getClusterId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (clusterId != null) {
            DedicatedResourceVO cluster = _dedicatedDao.findByClusterId(clusterId);
            if (cluster != null){
                clusters.add(cluster);
            }
        } else if (accountId != null) {
            // for domainId != null
            // right now, we made the decision to only list clusters associated
            // with this domain/account
            if (domain != null && domainId == account.getDomainId()) {
                clusters = _dedicatedDao.findClustersByAccountId(accountId);
            } else {
                throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
            }
        } else if (domainId != null && accountId == null) {
            clusters = _dedicatedDao.findClustersByDomainId(domainId);
        } else {
            clusters = _dedicatedDao.listClusters();
        }
        return clusters;
    }

    @Override
    public List<DedicatedResourceVO> listDedicatedHosts(ListDedicatedHostsCmd cmd) {
        List<DedicatedResourceVO> hosts = new ArrayList<DedicatedResourceVO>();
        Long hostId = cmd.getHostId();
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        if (hostId != null) {
            DedicatedResourceVO host = _dedicatedDao.findByHostId(hostId);
            if (host != null){
                hosts.add(host);
            }
        } else if (accountId != null) {
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
        } else {
            hosts = _dedicatedDao.listHosts();
        }
        return hosts;
    }

    @Override
    @DB
    @ActionEvent(eventType = EventTypes.EVENT_DEDICATE_RESOURCE_RELEASE, eventDescription = "Releasing dedicated resource")
    public boolean releaseDedicatedResource(Long zoneId, Long podId, Long clusterId, Long hostId) throws InvalidParameterValueException{
        DedicatedResourceVO resource = null;
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
        if (resource == null){
            throw new InvalidParameterValueException("No Dedicated Resource available to release");
        } else {
            Transaction txn = Transaction.currentTxn();
            txn.start();
            resourceId = resource.getId();
            if (!_dedicatedDao.remove(resourceId)) {
                throw new CloudRuntimeException("Failed to delete Resource " + resourceId);
            }
            txn.commit();
        }
        return true;
    }
}
