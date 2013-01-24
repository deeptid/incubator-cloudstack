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
package com.cloud.dedicated.manager;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.command.admin.cluster.AddClusterCmd;
import org.apache.cloudstack.api.command.admin.storage.ListS3sCmd;
import org.apache.cloudstack.api.command.admin.swift.AddSwiftCmd;
import org.apache.cloudstack.api.command.admin.cluster.DeleteClusterCmd;
import org.apache.cloudstack.api.command.admin.host.*;
import org.apache.cloudstack.api.command.admin.swift.ListSwiftsCmd;
import org.apache.cloudstack.api.command.admin.storage.AddS3Cmd;
import com.cloud.storage.S3;
import com.cloud.storage.S3VO;
import com.cloud.storage.s3.S3Manager;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.AgentManager.TapAgentsAction;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetHostStatsAnswer;
import com.cloud.agent.api.GetHostStatsCommand;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.UnsupportedAnswer;
import com.cloud.agent.api.UpdateHostPasswordCommand;
import com.cloud.agent.manager.AgentAttache;
import com.cloud.agent.manager.allocator.PodAllocator;
import com.cloud.agent.transport.Request;
import org.apache.cloudstack.api.ApiConstants;
import com.cloud.api.ApiDBUtils;
import org.apache.cloudstack.api.command.admin.host.AddHostCmd;
import org.apache.cloudstack.api.command.admin.host.CancelMaintenanceCmd;
import org.apache.cloudstack.api.command.admin.host.PrepareForMaintenanceCmd;
import org.apache.cloudstack.api.command.admin.host.UpdateHostCmd;
import org.apache.cloudstack.api.command.user.zone.ListZonesByCmd;
import org.apache.cloudstack.api.response.CapacityResponse;
import org.apache.cloudstack.api.response.PodResponse;

import com.cloud.capacity.Capacity;
import com.cloud.capacity.CapacityVO;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.capacity.dao.CapacityDaoImpl.SummedCapacity;
import com.cloud.cluster.ClusterManager;
import com.cloud.cluster.ManagementServerNode;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterIpAddressVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.Pod;
import com.cloud.dc.PodCluster;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.ClusterVSMMapDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterIpAddressDao;
import com.cloud.dc.dao.HostPodDao;

import com.cloud.dedicated.dao.DedicatedResourceDao;
import com.cloud.dedicated.dao.DedicatedResourceVO;
import com.cloud.api.commands.*;
import com.cloud.api.response.*;
import com.cloud.dedicated.services.DedicatedResources;
import com.cloud.dedicated.services.DedicatedService;

import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.DiscoveryException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceInUseException;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.ha.HighAvailabilityManager.WorkType;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostStats;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.Status.Event;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.host.dao.HostTagsDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.hypervisor.kvm.discoverer.KvmDummyResourceBase;
import com.cloud.network.IPAddressVO;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.org.Cluster;
import com.cloud.org.Grouping;
import com.cloud.org.Grouping.AllocationState;
import com.cloud.org.Managed;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.storage.GuestOSCategoryVO;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.storage.StoragePoolStatus;
import com.cloud.storage.StoragePoolVO;

import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;

import com.cloud.utils.component.Inject;
import com.cloud.utils.component.Manager;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;


@Local({DedicatedService.class })
public class DedicatedResourceManagerImpl implements DedicatedService {
    private static final Logger s_logger = Logger.getLogger(DedicatedResourceManagerImpl.class);

    private String                           _name;
    @Inject
    protected AccountDao                     _accountDao; 
    @Inject
    protected DomainDao                      _domainDao;
    @Inject
    protected DataCenterDao                  _dcDao;
    @Inject
    protected HostPodDao                     _podDao;
    @Inject
    protected ClusterDetailsDao              _clusterDetailsDao;
    @Inject
    protected ClusterDao                     _clusterDao;
    @Inject
    protected CapacityDao 					 _capacityDao;
    @Inject
    protected HostDao                        _hostDao;
    @Inject
    protected ConfigurationDao _configDao;
    @Inject
    protected HostTagsDao                    _hostTagsDao;
    @Inject
    protected DedicatedResourceDao           _dedicatedDao;


    
    @Override
    @DB
    public List<? extends DedicatedResources> dedicatePod(Long podId, Long domainId, Long accountId) {
    	
    	// verify parameters
        HostPodVO pod = _podDao.findById(podId);
        if (pod == null) {
            throw new InvalidParameterValueException("Unable to find pod by id " + podId);
        }
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        
        if (domain == null) {
            throw new InvalidParameterValueException("Unable to find the domain by id " + domainId);
        }
        
        if (account == null) {
    		throw new InvalidParameterValueException("Unable to find the account by id " + accountId);
    	}
        DedicatedResourceVO dedicatedPod = new DedicatedResourceVO(null, podId, null, null, null, null);        
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            dedicatedPod = _dedicatedDao.findByPodId(podId);
            if (domainId != null) {
            	dedicatedPod.setDomainId(domainId);
            	//check if account belongs to the domain id
                if (accountId != null) {
                	if (domainId == null || account == null || domainId != account.getDomainId()){
                		throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
                	} else {
                		dedicatedPod.setAccountId(accountId);
                	}
                }
            }   
            dedicatedPod = _dedicatedDao.persist(dedicatedPod);
            txn.commit();
        } catch (Exception e) {
            s_logger.error("Unable to Dedicate pod due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate pod. Please contact Cloud Support.");
        }
        
        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedPod);
        
    	return result;
    }
    
    @Override
    @DB
    public List<? extends DedicatedResources> dedicateCluster(Long clusterId, Long domainId, Long accountId) {
    	
    	// verify parameters
        ClusterVO cluster = _clusterDao.findById(clusterId);
        if (cluster == null) {
            throw new InvalidParameterValueException("Unable to find cluster by id " + clusterId);
        }
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        
        if (domain == null) {
            throw new InvalidParameterValueException("Unable to find the domain by id " + domainId);
        }
        
        if (account == null) {
    		throw new InvalidParameterValueException("Unable to find the account by id " + accountId);
    	}
        DedicatedResourceVO dedicatedCluster = new DedicatedResourceVO(null, null, clusterId, null, null, null);        
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            dedicatedCluster = _dedicatedDao.findByClusterId(clusterId);
            if (domainId != null) {
            	dedicatedCluster.setDomainId(domainId);
            	//check if account belongs to the domain id
                if (accountId != null) {
                	if (domainId == null || account == null || domainId != account.getDomainId()){
                		throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
                	} else {
                		dedicatedCluster.setAccountId(accountId);
                	}
                }
            }   
            dedicatedCluster = _dedicatedDao.persist(dedicatedCluster);
            txn.commit();
        } catch (Exception e) {
            s_logger.error("Unable to Dedicate pod due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate pod. Please contact Cloud Support.");
        }
        
        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedCluster);
        
    	return result;
    }
    
    @Override
    @DB
    public List<? extends DedicatedResources> dedicateHost(Long hostId, Long domainId, Long accountId) {
    	
    	// verify parameters
        HostVO host = _hostDao.findById(hostId);
        if (host == null) {
            throw new InvalidParameterValueException("Unable to find cluster by id " + hostId);
        }
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
        
        if (domain == null) {
            throw new InvalidParameterValueException("Unable to find the domain by id " + domainId);
        }
        
        if (account == null) {
    		throw new InvalidParameterValueException("Unable to find the account by id " + accountId);
    	}
        DedicatedResourceVO dedicatedHost = new DedicatedResourceVO(null, null, null, hostId, null, null);        
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            dedicatedHost = _dedicatedDao.findByHostId(hostId);
            if (domainId != null) {
            	dedicatedHost.setDomainId(domainId);
            	//check if account belongs to the domain id
                if (accountId != null) {
                	if (domainId == null || account == null || domainId != account.getDomainId()){
                		throw new InvalidParameterValueException("Please specify the domain id of the account: " + account.getAccountName());
                	} else {
                		dedicatedHost.setAccountId(accountId);
                	}
                }
            }   
            dedicatedHost = _dedicatedDao.persist(dedicatedHost);
            txn.commit();
        } catch (Exception e) {
            s_logger.error("Unable to Dedicate pod due to " + e.getMessage(), e);
            throw new CloudRuntimeException("Failed to dedicate pod. Please contact Cloud Support.");
        }
        
        List<DedicatedResourceVO> result = new ArrayList<DedicatedResourceVO>();
        result.add(dedicatedHost);
        
    	return result;
    }
    
    @Override
    public DedicatePodResponse createDedicatePodResponse(DedicatedResources resource) {
        DedicatePodResponse dedicatePodResponse = new DedicatePodResponse();
        dedicatePodResponse.setPodId(resource.getPodId());
        dedicatePodResponse.setDomainId(resource.getDomainId());
        dedicatePodResponse.setAccountId(resource.getDomainId());
        dedicatePodResponse.setObjectName("pod");
        return dedicatePodResponse;
    }
    
    @Override
    public DedicateClusterResponse createDedicateClusterResponse(DedicatedResources resource) {
        DedicateClusterResponse dedicateClusterResponse = new DedicateClusterResponse();
        dedicateClusterResponse.setClusterId(resource.getClusterId());
        dedicateClusterResponse.setDomainId(resource.getDomainId());
        dedicateClusterResponse.setAccountId(resource.getDomainId());
        dedicateClusterResponse.setObjectName("cluster");       
        return dedicateClusterResponse;        
    }
    
    @Override
    public DedicateHostResponse createDedicateHostResponse(DedicatedResources resource) {
        DedicateHostResponse dedicateHostResponse = new DedicateHostResponse();
        dedicateHostResponse.setHostId(resource.getHostId());
        dedicateHostResponse.setDomainId(resource.getDomainId());
        dedicateHostResponse.setAccountId(resource.getDomainId());
        dedicateHostResponse.setObjectName("cluster");       
        return dedicateHostResponse;        
    }

    @Override
	public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(DedicatePodCmd.class);
        cmdList.add(DedicateClusterCmd.class);
        cmdList.add(DedicateHostCmd.class);
        cmdList.add(ListDedicatedPodsCmd.class);
        return cmdList;
	}
    
    @Override
    public List<DedicatedResourceVO> listDedicatedPods(ListDedicatedPodsCmd cmd) {
    	List<DedicatedResourceVO> pods = null;
        Long domainId = cmd.getDomainId();
        Long accountId = cmd.getAccountId();
        DomainVO domain = _domainDao.findById(domainId);
        AccountVO account = _accountDao.findById(accountId);
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
        return pods;
    }
}
