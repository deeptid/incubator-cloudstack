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
package org.apache.cloudstack.affinity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.log4j.Logger;

import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.AffinityConflictException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

@Local(value = AffinityGroupProcessor.class)
public class ExplicitDedicationProcessor extends AffinityProcessorBase implements AffinityGroupProcessor {

    private static final Logger s_logger = Logger.getLogger(ExplicitDedicationProcessor.class);
    @Inject
    protected UserVmDao _vmDao;
    @Inject
    protected VMInstanceDao _vmInstanceDao;
    @Inject
    protected DataCenterDao _dcDao;
    @Inject 
    protected DedicatedResourceDao _dedicatedDao;
    @Inject 
    protected HostPodDao _podDao;
    @Inject 
    protected ClusterDao _clusterDao;
    @Inject
    protected HostDao _hostDao;
    @Inject
    protected DomainDao _domainDao;

    /**
     * This method will process the affinity group of type 'Explicit Dedication' for a deployment of a VM that demands dedicated resources.
     * For ExplicitDedicationProcessor we need to add dedicated resources into the IncludeList based on the level we have dedicated resources available.
     * For eg. if admin dedicates a pod to a domain, then all the user in that domain can use the resources of that pod.
     * We need to take care of the situation when dedicated resources further have resources dedicated to sub-domain/account.
     * This IncludeList is then used to update the avoidlist for a given data center.
     */
    @Override
    public void process(VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan,
            ExcludeList avoid) throws AffinityConflictException {
        VirtualMachine vm = vmProfile.getVirtualMachine();
        DataCenter dc = _dcDao.findById(vm.getDataCenterId());
        long domainId = vm.getDomainId();
        long accountId = vm.getAccountId();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Processing affinity group of type 'ExplicitDedication' for VM Id: " + vm.getId());
        }

        List<DedicatedResourceVO> dr = _dedicatedDao.listByAccountId(accountId);
        List<DedicatedResourceVO> drOfDomain = searchInDomainResources(domainId);
        List<DedicatedResourceVO> drOfParentDomain = searchInParentDomainResources(domainId);

        if (dr != null && dr.size() != 0) {
            avoid = updateAvoidList(dr, avoid, dc);
        } else if(drOfDomain != null && drOfDomain.size() != 0){
            avoid = updateAvoidList(drOfDomain, avoid, dc);
        } else if(drOfParentDomain != null && drOfParentDomain.size() != 0){
            avoid = updateAvoidList(drOfParentDomain, avoid, dc);
        } else {
            avoid.addDataCenter(dc.getId());
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("No dedicated resources available for this domain or account");
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("ExplicitDedicationProcessor returns Avoid List as: Deploy avoids pods: " + avoid.getPodsToAvoid() + ", clusters: "
                    + avoid.getClustersToAvoid() + ", hosts: " + avoid.getHostsToAvoid());
        }
    }

    private ExcludeList updateAvoidList(List<DedicatedResourceVO> dedicatedResources, ExcludeList avoidList, DataCenter dc) {
        ExcludeList includeList = new ExcludeList();
        for (DedicatedResourceVO dr : dedicatedResources) {
            if (dr.getHostId() != null){
                includeList.addHost(dr.getHostId());
                HostVO dedicatedHost = _hostDao.findById(dr.getHostId());
                includeList.addCluster(dedicatedHost.getClusterId());
                includeList.addPod(dedicatedHost.getPodId());
            }

            if (dr.getClusterId() != null) {
                includeList.addCluster(dr.getClusterId());
                //add all hosts inside this in includeList
                List<HostVO> hostList = _hostDao.findByClusterId(dr.getClusterId());
                for (HostVO host : hostList) {
                    DedicatedResourceVO dHost = _dedicatedDao.findByHostId(host.getId());
                    if (dHost != null) {
                        avoidList.addHost(host.getId());
                    } else {
                        includeList.addHost(host.getId());
                    }
                }
                ClusterVO dedicatedCluster = _clusterDao.findById(dr.getClusterId());
                includeList.addPod(dedicatedCluster.getPodId());
            }

            if (dr.getPodId() != null) {
                includeList.addPod(dr.getPodId());
                //add all cluster under this pod in includeList
                List<ClusterVO> clusterList = _clusterDao.listByPodId(dr.getPodId());
                for (ClusterVO cluster : clusterList) {
                    if (_dedicatedDao.findByClusterId(cluster.getId()) != null) {
                        avoidList.addCluster(cluster.getId());
                    } else {
                        includeList.addCluster(cluster.getId());
                    }
                }
                //add all hosts inside this pod in includeList
                List<HostVO> hostList = _hostDao.findByPodId(dr.getPodId());
                for (HostVO host : hostList) {
                    if (_dedicatedDao.findByHostId(host.getId()) != null) {
                        avoidList.addHost(host.getId());
                    } else {
                        includeList.addHost(host.getId());
                    }
                }
            }

            if (dr.getDataCenterId() != null) {
                includeList.addDataCenter(dr.getDataCenterId());
                //add all Pod under this data center in includeList
                List<HostPodVO> podList = _podDao.listByDataCenterId(dr.getDataCenterId());
                for (HostPodVO pod : podList) {
                    if (_dedicatedDao.findByPodId(pod.getId()) != null) {
                        avoidList.addPod(pod.getId());
                    } else {
                        includeList.addPod(pod.getId());
                    }
                }
                List<ClusterVO> clusterList = _clusterDao.listClustersByDcId(dr.getDataCenterId());
                for (ClusterVO cluster : clusterList) {
                    if (_dedicatedDao.findByClusterId(cluster.getId()) != null) {
                        avoidList.addCluster(cluster.getId());
                    } else {
                        includeList.addCluster(cluster.getId());
                    }
                }
                //add all hosts inside this in includeList
                List<HostVO> hostList = _hostDao.listByDataCenterId(dr.getDataCenterId());
                for (HostVO host : hostList) {
                    if (_dedicatedDao.findByHostId(host.getId()) != null) {
                        avoidList.addHost(host.getId());
                    } else {
                        includeList.addHost(host.getId());
                    }
                }
            }
        }
        //Update avoid list using includeList.
        //add resources in avoid list which are not in include list.

        List<HostPodVO> pods = _podDao.listByDataCenterId(dc.getId());
        List<ClusterVO> clusters = _clusterDao.listClustersByDcId(dc.getId());
        List<HostVO> hosts = _hostDao.listByDataCenterId(dc.getId());
        Set<Long> podsInIncludeList = includeList.getPodsToAvoid();
        Set<Long> clustersInIncludeList = includeList.getClustersToAvoid();
        Set<Long> hostsInIncludeList = includeList.getHostsToAvoid();

        for (HostPodVO pod : pods){
            if (podsInIncludeList != null && !podsInIncludeList.contains(pod.getId())) {
                avoidList.addPod(pod.getId());
            }
        }

        for (ClusterVO cluster : clusters) {
            if (clustersInIncludeList != null && !clustersInIncludeList.contains(cluster.getId())) {
                avoidList.addCluster(cluster.getId());
            }
        }

        for (HostVO host : hosts) {
            if (hostsInIncludeList != null && !hostsInIncludeList.contains(host.getId())) {
                avoidList.addHost(host.getId());
            }
        }
        return avoidList;
    }

    private List<DedicatedResourceVO> searchInParentDomainResources(long domainId) {
        List<Long> domainIds = getDomainParentIds(domainId);
        List<DedicatedResourceVO> dr = new ArrayList<DedicatedResourceVO>();
        for (Long id : domainIds) {
            List<DedicatedResourceVO> resource = _dedicatedDao.listByDomainId(id);
            if(resource != null) {
                dr.addAll(resource);
            }
        }
        if (dr != null && dr.size() != 0) {
            return dr;
        } else {
            return null;
        }
    }

    private List<DedicatedResourceVO> searchInDomainResources(long domainId) {
        List<DedicatedResourceVO> dr = _dedicatedDao.listByDomainId(domainId);
        if (dr != null && dr.size() != 0) {
            return dr;
        } else {
            return null;
        }
    }

    private List<Long> getDomainParentIds(long domainId) {
        DomainVO domainRecord = _domainDao.findById(domainId);
        List<Long> domainIds = new ArrayList<Long>();
        domainIds.add(domainRecord.getId());
        while (domainRecord.getParent() != null ){
            domainRecord = _domainDao.findById(domainRecord.getParent());
            domainIds.add(domainRecord.getId());
        }
        return domainIds;
    }

}
