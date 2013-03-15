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

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.cloudstack.affinity.dao.AffinityGroupDao;
import org.apache.cloudstack.affinity.dao.AffinityGroupVMMapDao;
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
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

@Local(value = AffinityGroupProcessor.class)
public class ExplicitDedicationProcessor extends AdapterBase implements AffinityGroupProcessor {

    private static final Logger s_logger = Logger.getLogger(ExplicitDedicationProcessor.class);
    @Inject
    protected UserVmDao _vmDao;
    @Inject
    protected VMInstanceDao _vmInstanceDao;
    @Inject
    protected AffinityGroupDao _affinityGroupDao;
    @Inject
    protected AffinityGroupVMMapDao _affinityGroupVMMapDao;
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

    @Override
    public void process(VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan,
            ExcludeList avoid)
                    throws AffinityConflictException {
        VirtualMachine vm = vmProfile.getVirtualMachine();
        DataCenter dc = _dcDao.findById(vm.getDataCenterId());
        long domainId = vm.getDomainId();
        long accountId = vm.getAccountId();
        List<AffinityGroupVMMapVO> vmGroupMappings = _affinityGroupVMMapDao.findByVmIdType(vm.getId(), getType());

        for (AffinityGroupVMMapVO vmGroupMapping : vmGroupMappings) {
            if (vmGroupMapping != null) {
                AffinityGroupVO group = _affinityGroupDao.findById(vmGroupMapping.getAffinityGroupId());
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Processing affinity group " + group.getName() + " of type: " + group.getType() + " for VM Id: " + vm.getId());
                }

                DedicatedResourceVO dedicatedZone = _dedicatedDao.findByZoneId(dc.getId());
                if (dedicatedZone != null){
                    //check dedication for account/domain; all sub-domains can access parent's domain dedicated resources
                    if (dedicatedZone.getDomainId() != null && !(getDomainChildIds(dedicatedZone.getDomainId()).contains(domainId))) {
                        throw new CloudRuntimeException("Zone cannot be used for explicit dedication for this domain " + domainId);
                    } 
                    if (dedicatedZone.getAccountId() != null && dedicatedZone.getAccountId() != accountId) {
                        throw new CloudRuntimeException("Zone cannot be used for explicit dedication for this account " + accountId);
                    }
                } else {
                    //add every thing in avoidList under this Zone
                    List<HostPodVO> pods = _podDao.listByDataCenterId(dc.getId());
                    List<ClusterVO> clusters = _clusterDao.listClustersByDcId(dc.getId());
                    List<HostVO> hosts = _hostDao.listByDataCenterId(dc.getId());
                    for (HostPodVO pod : pods){
                        avoid.addPod(pod.getId());
                    }
                    for (ClusterVO cluster : clusters) {
                        avoid.addCluster(cluster.getId());
                    }
                    for (HostVO host : hosts) {
                        avoid.addHost(host.getId());
                    }

                    //Remove Dedicated Resources form Avoid List
                    List<DedicatedResourceVO> dedicatedPodsByAccount = _dedicatedDao.findPodsByAccountId(accountId);
                    List<DedicatedResourceVO> dedicatedPodsByDomain = _dedicatedDao.findPodsByDomainId(domainId);
                    //Can use dedicated pods of parent domain
                    List<Long> parentDomainIds = getDomainParentIds(domainId);
                    for (Long id : parentDomainIds) {
                        List<DedicatedResourceVO> dPods = _dedicatedDao.findPodsByDomainId(id);
                        if (dPods != null) {
                            dedicatedPodsByDomain.addAll(dPods);
                        }
                    }
                    dedicatedPodsByAccount.addAll(dedicatedPodsByDomain);
                    if (dedicatedPodsByAccount != null && !dedicatedPodsByAccount.isEmpty()){
                        for (DedicatedResourceVO dedicatedPod : dedicatedPodsByAccount){
                            //remove dedicated pod from the Avoid list
                            avoid.removePod(dedicatedPod.getPodId());
                            // remove all resources under this Pod from the Avoid list
                            List<ClusterVO> clustersInPod = _clusterDao.listByPodId(dedicatedPod.getPodId());
                            for (ClusterVO cluster : clustersInPod){
                                avoid.removeCluster(cluster.getId());
                            }
                            List<HostVO> hostsInPod = _hostDao.findByPodId(dedicatedPod.getPodId());
                            for (HostVO host: hostsInPod){
                                avoid.removeHost(host.getId());
                            }
                        }
                    }

                    List<DedicatedResourceVO> dedicatedClustersByAccount = _dedicatedDao.findClustersByAccountId(accountId);
                    List<DedicatedResourceVO> dedicatedClustersByDomain = _dedicatedDao.findClustersByDomainId(domainId);
                    //Can use dedicated pods of parent domain
                    for (Long id : parentDomainIds) {
                        List<DedicatedResourceVO> dClusters = _dedicatedDao.findClustersByDomainId(id);
                        if (dClusters != null) {
                            dedicatedClustersByDomain.addAll(dClusters);
                        }
                    }
                    dedicatedClustersByAccount.addAll(dedicatedClustersByDomain);
                    if (dedicatedClustersByAccount != null && dedicatedClustersByAccount.size() != 0){
                        for (DedicatedResourceVO dedicatedCluster : dedicatedClustersByAccount){
                            //remove dedicated cluster from the Avoid list
                            avoid.removeCluster(dedicatedCluster.getClusterId());
                            // remove all resources under this Cluster from the Avoid list
                            List<HostVO> hostsInCluster = _hostDao.findByClusterId(dedicatedCluster.getClusterId());
                            for (HostVO host: hostsInCluster){
                                avoid.removeHost(host.getId());
                            }
                            //remove resources above this cluster from the Avoid List
                            ClusterVO cluster = _clusterDao.findById(dedicatedCluster.getClusterId());
                            HostPodVO pod = _podDao.findById(cluster.getPodId());
                            avoid.removePod(pod.getId());
                        }
                    }

                    List<DedicatedResourceVO> dedicatedHostsByAccount = _dedicatedDao.findHostsByAccountId(accountId);
                    List<DedicatedResourceVO> dedicatedHostsByDomain = _dedicatedDao.findHostsByDomainId(domainId);
                    //Can use dedicated pods of parent domain
                    for (Long id : parentDomainIds) {
                        List<DedicatedResourceVO> dHosts = _dedicatedDao.findHostsByDomainId(id);
                        if (dHosts != null) {
                            dedicatedClustersByDomain.addAll(dHosts);
                        }
                    }
                    dedicatedHostsByAccount.addAll(dedicatedHostsByDomain);
                    if ( dedicatedHostsByAccount != null && dedicatedHostsByAccount.size() != 0){
                        for (DedicatedResourceVO dedicatedHost : dedicatedHostsByAccount){
                            //remove all dedicated host from the AvoidList
                            avoid.removeHost(dedicatedHost.getHostId());
                            //remove resources above this host from the AvoidList
                            HostVO host = _hostDao.findById(dedicatedHost.getHostId());
                            avoid.removeCluster(host.getClusterId());
                            avoid.removePod(host.getPodId());
                        }
                    }
                }
            }
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
    public String getType() {
        return "ExplicitDedication";
    }

}
