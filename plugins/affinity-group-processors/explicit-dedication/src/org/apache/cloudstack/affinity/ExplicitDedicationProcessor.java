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

    @Override
    public void process(VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan,
            ExcludeList avoid)
            throws AffinityConflictException {
        VirtualMachine vm = vmProfile.getVirtualMachine();
        DataCenter dc = _dcDao.findById(vm.getDataCenterId());
        long domainId = vm.getDomainId();
        long accountId = vm.getAccountId();
        AffinityGroupVMMapVO vmGroupMapping = _affinityGroupVMMapDao.findByVmIdType(vm.getId(), getType());

        if (vmGroupMapping != null) {
            AffinityGroupVO group = _affinityGroupDao.findById(vmGroupMapping.getAffinityGroupId());
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Processing affinity group " + group.getName() + " of type: " + group.getType() + " for VM Id: " + vm.getId());
            }
            DedicatedResourceVO dedicatedZone = _dedicatedDao.findByZoneId(dc.getId());
            if (dedicatedZone != null){
                //check if zone is implicitly dedicated
                if (dedicatedZone.getImplicitDedication() == true || dedicatedZone.getDomainId() == domainId || dedicatedZone.getAccountId() == accountId) {
                    throw new CloudRuntimeException("Zone cannot be used for explicit dedication.");
                }
            }
            //add every thing in avoidList under this Zone
            List<HostPodVO> pods = _podDao.listByDataCenterId(dc.getId());
            List<ClusterVO> clusters = _clusterDao.listByZoneId(dc.getId());
            List<HostVO> hosts = _hostDao.listAllUpAndEnabledNonHAHosts(null, null, null, dc.getId(), null);
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

            //Using ExcludeList to store the scopeList
            //ExcludeList scopeList = null;
            //setScope(dc.getId(), dedicatedPodsByAccount, dedicatedClustersByAccount, dedicatedHostsByAccount, scopeList);
        }
    }

    private void setScope(long dcId, List<DedicatedResourceVO> dedicatedPodsByAccount,
            List<DedicatedResourceVO> dedicatedClustersByAccount,
            List<DedicatedResourceVO> dedicatedHostsByAccount, ExcludeList scopeList) {
        if (dedicatedPodsByAccount != null && dedicatedPodsByAccount.size() != 0) {
            if (dedicatedPodsByAccount.size() == 1){
                DedicatedResourceVO dedicatedPod = dedicatedPodsByAccount.get(0);
                scopeList.addPod(dedicatedPod.getPodId());
            } else {
                scopeList.addDataCenter(dcId);
            }
        }
        if (dedicatedClustersByAccount != null && dedicatedClustersByAccount.size() != 0) {
            if (dedicatedClustersByAccount.size() == 1) {
                DedicatedResourceVO dedicatedCluster = dedicatedClustersByAccount.get(0);
                scopeList.addCluster(dedicatedCluster.getClusterId());
            } else {
                for (DedicatedResourceVO dedicatedCluster : dedicatedClustersByAccount) {
                    Long clusterId = dedicatedCluster.getClusterId();
                    ClusterVO cluster = _clusterDao.findById(clusterId);
                    if (! scopeList.getPodsToAvoid().contains(cluster.getPodId())) {
                        scopeList.addPod(cluster.getPodId());
                    }
                }
            }
        }
        if (dedicatedHostsByAccount != null && dedicatedHostsByAccount.size() != 0) {
            if (dedicatedHostsByAccount.size() == 1) {
                DedicatedResourceVO dedicatedHost = dedicatedHostsByAccount.get(0);
                scopeList.addHost(dedicatedHost.getHostId());
            } else {
                for (DedicatedResourceVO dedicateHost : dedicatedHostsByAccount) {
                    HostVO host = _hostDao.findById(dedicateHost.getHostId());
                    if (scopeList.getClustersToAvoid().contains(host.getClusterId())) {
                        scopeList.addCluster(host.getClusterId());
                    }
                }
            }
        }

    }

    @Override
    public String getType() {
        return "ExplicitDedication";
    }

}
