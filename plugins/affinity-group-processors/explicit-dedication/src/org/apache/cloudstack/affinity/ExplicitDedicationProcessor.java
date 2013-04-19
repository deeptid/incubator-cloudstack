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
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Processing affinity group of type 'ExplicitDedication' for VM Id: " + vm.getId());
        }

        List<HostPodVO> pods = _podDao.listByDataCenterId(dc.getId());
        List<ClusterVO> clusters = _clusterDao.listClustersByDcId(dc.getId());
        List<HostVO> hosts = _hostDao.listByDataCenterId(dc.getId());
        List<Long> parentDomainIds = getDomainParentIds(domainId);

        //add every thing in avoidList under this Zone
        for (HostPodVO pod : pods){
            avoid.addPod(pod.getId());
        }
        for (ClusterVO cluster : clusters) {
            avoid.addCluster(cluster.getId());
        }
        for (HostVO host : hosts) {
            avoid.addHost(host.getId());
        }

        //Dedicated Zones

        List<DedicatedResourceVO> dedicatedZonesByAccount = _dedicatedDao.findZonesByAccountId(accountId);
        List<DedicatedResourceVO> dedicatedZonesByDomain = null;
        if (dedicatedZonesByAccount.size() == 0) {
            dedicatedZonesByDomain = _dedicatedDao.findZonesByDomainId(domainId);
        }
        if (dedicatedZonesByAccount.size() == 0 && dedicatedZonesByDomain.size() == 0){
            //Can use dedicated Zones of parent domain
            for (Long id : parentDomainIds) {
                List<DedicatedResourceVO> dZones = _dedicatedDao.findZonesByDomainId(id);
                if (dZones != null && dZones.size() !=0) {
                    dedicatedZonesByDomain.addAll(dZones);
                }
            }
        }
        if (dedicatedZonesByDomain != null){
            dedicatedZonesByAccount.addAll(dedicatedZonesByDomain);
        }
        if (dedicatedZonesByAccount != null && !dedicatedZonesByAccount.isEmpty()){
            //remove everything under this zone
            for (HostPodVO pod : pods){
                avoid.removePod(pod.getId());
            }
            for (ClusterVO cluster : clusters) {
                avoid.removeCluster(cluster.getId());
            }
            for (HostVO host : hosts) {
                avoid.removeHost(host.getId());
            }
        }

        //Dedicated Pods

        List<DedicatedResourceVO> dedicatedPodsByAccount = _dedicatedDao.findPodsByAccountId(accountId);
        List<DedicatedResourceVO> dedicatedPodsByDomain = null;
        if (dedicatedPodsByAccount.size() == 0) {
            dedicatedPodsByDomain = _dedicatedDao.findPodsByDomainId(domainId);
        }
        if (dedicatedPodsByAccount.size() == 0 && dedicatedPodsByDomain.size() == 0) {
            //Can use dedicated pods of parent domain
            for (Long id : parentDomainIds) {
                List<DedicatedResourceVO> dPods = _dedicatedDao.findPodsByDomainId(id);
                if (dPods != null && dPods.size() != 0) {
                    dedicatedPodsByDomain.addAll(dPods);
                }
            }
        }
        if (dedicatedPodsByDomain != null) {
            dedicatedPodsByAccount.addAll(dedicatedPodsByDomain);
        }
        if (dedicatedPodsByAccount != null && !dedicatedPodsByAccount.isEmpty()){
            for (DedicatedResourceVO dedicatedPod : dedicatedPodsByAccount){
                //remove dedicated pod from the Avoid list
                avoid.removePod(dedicatedPod.getPodId());
                // remove all resources under this Pod from the Avoid list
                List<ClusterVO> clustersInPod = _clusterDao.listByPodId(dedicatedPod.getPodId());
                for (ClusterVO cluster : clustersInPod){
                    //only remove resources which are non-dedicated
                    if (_dedicatedDao.findByClusterId(cluster.getId()) == null) {
                        avoid.removeCluster(cluster.getId());
                    }
                }
                List<HostVO> hostsInPod = _hostDao.findByPodId(dedicatedPod.getPodId());
                for (HostVO host: hostsInPod){
                    //only remove resources which are non-dedicated
                    if (_dedicatedDao.findByHostId(host.getId()) == null) {
                        avoid.removeHost(host.getId());
                    } 
                }
                /*add other Pods in avoid list
                eg: if zone is dedicated to parent and this pod to dedicated to sub-domain, 
                then we should not allow deployment on other pods for the sub-domain user*/
                for (HostPodVO podInZone : pods) {
                    if (podInZone.getId() != dedicatedPod.getPodId()){
                        avoid.addHost(podInZone.getId());
                    }
                }
            }
        }

        //Dedicated Clusters

        List<DedicatedResourceVO> dedicatedClustersByAccount = _dedicatedDao.findClustersByAccountId(accountId);
        List<DedicatedResourceVO> dedicatedClustersByDomain = null;
        if (dedicatedClustersByAccount.size() == 0){
            dedicatedClustersByDomain = _dedicatedDao.findClustersByDomainId(domainId);
        }
        if (dedicatedClustersByAccount.size() == 0  && dedicatedClustersByDomain.size() == 0) {
            //Can use dedicated clusters of parent domain
            for (Long id : parentDomainIds) {
                List<DedicatedResourceVO> dClusters = _dedicatedDao.findClustersByDomainId(id);
                if (dClusters != null && dClusters.size() != 0) {
                    dedicatedClustersByDomain.addAll(dClusters);
                }
            }
        }
        if (dedicatedClustersByDomain != null) {
            dedicatedClustersByAccount.addAll(dedicatedClustersByDomain);
        }
        if (dedicatedClustersByAccount != null && dedicatedClustersByAccount.size() != 0) {
            for (DedicatedResourceVO dedicatedCluster : dedicatedClustersByAccount) {
                //remove dedicated cluster from the Avoid list
                avoid.removeCluster(dedicatedCluster.getClusterId());
                // remove all resources under this Cluster from the Avoid list
                List<HostVO> hostsInCluster = _hostDao.findByClusterId(dedicatedCluster.getClusterId());
                for (HostVO host: hostsInCluster){
                    //only remove resources which are non-dedicated
                    if (_dedicatedDao.findByHostId(host.getId()) == null){
                        avoid.removeHost(host.getId());
                    }
                }
                //remove resources above this cluster from the Avoid List
                ClusterVO cluster = _clusterDao.findById(dedicatedCluster.getClusterId());
                HostPodVO podOfDedicatedCluster = _podDao.findById(cluster.getPodId());
                avoid.removePod(cluster.getPodId());
                //dedicated clusters need to be added in the avoid list
                List<ClusterVO> clusterList = _clusterDao.listByPodId(cluster.getPodId());
                for (ClusterVO clusterInPod : clusterList) {
                    if (clusterInPod.getId() != dedicatedCluster.getClusterId()){
                        avoid.addHost(clusterInPod.getId());
                    }
                }
                for(HostPodVO podInDc : pods) {
                    if (podInDc.getId() != podOfDedicatedCluster.getId()){
                        avoid.addPod(podInDc.getId());
                    }
                }
            }
        }

        //Dedicated Hosts

        List<DedicatedResourceVO> dedicatedHostsByAccount = _dedicatedDao.findHostsByAccountId(accountId);
        List<DedicatedResourceVO> dedicatedHostsByDomain = null;
        if (dedicatedHostsByAccount.size() == 0){
            //check domain resource if nothing is dedicated to this account
            dedicatedHostsByDomain = _dedicatedDao.findHostsByDomainId(domainId);
        }
        //Can use dedicated pods of parent domain if nothing is dedicated to this account/domain
        if (dedicatedHostsByAccount.size() == 0 && dedicatedHostsByDomain.size() == 0) {
            for (Long id : parentDomainIds) {
                List<DedicatedResourceVO> dHosts = _dedicatedDao.findHostsByDomainId(id);
                if (dHosts != null && dHosts.size() != 0) {
                    for (DedicatedResourceVO host : dHosts) {
                        if (host.getAccountId() == null) {
                            dedicatedHostsByDomain.addAll(dHosts);
                        }
                    }
                }
            }
        }

        if (dedicatedHostsByDomain != null) {
            dedicatedHostsByAccount.addAll(dedicatedHostsByDomain);
        }
        if (dedicatedHostsByAccount != null && dedicatedHostsByAccount.size() != 0){
            for (DedicatedResourceVO dedicatedHost : dedicatedHostsByAccount){
                //remove all dedicated host from the AvoidList
                avoid.removeHost(dedicatedHost.getHostId());
                //remove resources above this host from the AvoidList
                HostVO host = _hostDao.findById(dedicatedHost.getHostId());
                avoid.removeCluster(host.getClusterId());
                /*then add all host accept dedicated host into avoid list; 
                    eg: if cluster is dedicated to parent and host to dedicated to sub-domain, 
                    then we should not allow deployment on other host for the sub-domain user*/
                List<HostVO> hostList = _hostDao.findByClusterId(host.getClusterId());
                for (HostVO hostInCluster : hostList) {
                    if (hostInCluster.getId() != dedicatedHost.getHostId()){
                        avoid.addHost(hostInCluster.getId());
                    }
                }
                ClusterVO clusterOfDedicatedHost = _clusterDao.findById(host.getClusterId());
                avoid.removePod(host.getPodId());
                List<ClusterVO> clusterList = _clusterDao.listByPodId(host.getPodId());
                for (ClusterVO clusterInPod : clusterList) {
                    if (clusterInPod.getId() != clusterOfDedicatedHost.getId()){
                        avoid.addCluster(clusterInPod.getId());
                    }
                }
                HostPodVO podOfDedicatedHost = _podDao.findById(host.getPodId());
                for(HostPodVO podInDc : pods) {
                    if (podInDc.getId() != podOfDedicatedHost.getId()){
                        avoid.addPod(podInDc.getId());
                    }
                }
            }
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("ExplicitDedicationProcessor returns Avoid List as: Deploy avoids pods: " + avoid.getPodsToAvoid() + ", clusters: "
                    + avoid.getClustersToAvoid() + ", hosts: " + avoid.getHostsToAvoid());
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

    @Override
    public String getType() {
        return "ExplicitDedication";
    }

}
