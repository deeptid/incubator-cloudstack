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
package com.cloud.deploy;

import java.util.List;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.cloudstack.affinity.AffinityGroupProcessor;
import org.apache.cloudstack.affinity.AffinityGroupVMMapVO;
import org.apache.cloudstack.affinity.AffinityGroupVO;
import org.apache.cloudstack.affinity.dao.AffinityGroupDao;
import org.apache.cloudstack.affinity.dao.AffinityGroupVMMapDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.exception.AffinityConflictException;
import com.cloud.exception.InsufficientServerCapacityException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.Manager;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

@Component
@Local(value = { DeploymentPlanningManager.class })
public class DeploymentPlanningManagerImpl extends ManagerBase implements DeploymentPlanningManager, Manager {

    private static final Logger s_logger = Logger.getLogger(DeploymentPlanningManagerImpl.class);
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
    protected HostPodDao _podDao;
    @Inject
    protected ClusterDao _clusterDao;
    @Inject
    protected HostDao _hostDao; 
    @Inject
    protected DedicatedResourceDao _dedicatedDao;

    protected List<DeploymentPlanner> _planners;
    public List<DeploymentPlanner> getPlanners() {
        return _planners;
    }
    public void setPlanners(List<DeploymentPlanner> _planners) {
        this._planners = _planners;
    }

    protected List<AffinityGroupProcessor> _affinityProcessors;
    public List<AffinityGroupProcessor> getAffinityGroupProcessors() {
        return _affinityProcessors;
    }
    public void setAffinityGroupProcessors(List<AffinityGroupProcessor> affinityProcessors) {
        this._affinityProcessors = affinityProcessors;
    }

    @Override
    public DeployDestination planDeployment(VirtualMachineProfile<? extends VirtualMachine> vmProfile,
            DeploymentPlan plan, ExcludeList avoids) throws InsufficientServerCapacityException,
            AffinityConflictException {

        // call affinitygroup chain
        VirtualMachine vm = vmProfile.getVirtualMachine();
        long vmGroupCount = _affinityGroupVMMapDao.countAffinityGroupsForVm(vm.getId());
        DataCenter dc = _dcDao.findById(vm.getDataCenterId());

        if (vmGroupCount > 0) {
            for (AffinityGroupProcessor processor : _affinityProcessors) {
                processor.process(vmProfile, plan, avoids);
            }
        }
        boolean isExplicit = false;
        // check affinity group of type Explicit dedication exists
        List<AffinityGroupVMMapVO> affinityGroupList = _affinityGroupVMMapDao.listByInstanceId(vm.getId());
        if (affinityGroupList != null) {
            for (AffinityGroupVMMapVO affinityGroup : affinityGroupList) {
                AffinityGroupVO ag = _affinityGroupDao.findById(affinityGroup.getAffinityGroupId());
                String agType = ag.getType(); 
                if (agType.equals("ExplicitDedication")) {
                    isExplicit = true;
                }
            }
        }

        if (!isExplicit && vm.getType() == VirtualMachine.Type.User) {
            //add explicitly dedicated resources in avoidList
            DedicatedResourceVO dedicatedZone = _dedicatedDao.findByZoneId(dc.getId());
            if (dedicatedZone != null) {
                throw new CloudRuntimeException("Failed to deploy VM. Zone " + dc.getName() + " is dedicated.");
            }
            List<HostPodVO> podsInDc = _podDao.listByDataCenterId(dc.getId());
            for (HostPodVO pod : podsInDc) {
                DedicatedResourceVO dedicatedPod = _dedicatedDao.findByPodId(pod.getId());
                if (dedicatedPod != null) {
                    avoids.addPod(dedicatedPod.getPodId());
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Cannot use this dedicated pod " + pod.getName() + ".");
                    }
                }
            }
            List<ClusterVO> clusterInDc = _clusterDao.listClustersByDcId(dc.getId());
            for (ClusterVO cluster : clusterInDc) {
                DedicatedResourceVO dedicatedCluster = _dedicatedDao.findByClusterId(cluster.getId());
                if (dedicatedCluster != null) {
                    avoids.addCluster(dedicatedCluster.getClusterId());
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Cannot use this dedicated Cluster " + cluster.getName() + ".");
                    }
                }
            }
            List<HostVO> hostInDc = _hostDao.listByDataCenterId(dc.getId());
            for (HostVO host : hostInDc) {
                DedicatedResourceVO dedicatedHost = _dedicatedDao.findByHostId(host.getId());
                if (dedicatedHost != null) {
                    avoids.addHost(dedicatedHost.getHostId());
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Cannot use this dedicated host " + host.getName() + ".");
                    }
                }
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Deploy avoids pods: " + avoids.getPodsToAvoid() + ", clusters: "
                    + avoids.getClustersToAvoid() + ", hosts: " + avoids.getHostsToAvoid());
        }

        // call planners
        DeployDestination dest = null;
        for (DeploymentPlanner planner : _planners) {
            if (planner.canHandle(vmProfile, plan, avoids)) {
                dest = planner.plan(vmProfile, plan, avoids);
            } else {
                continue;
            }
            if (dest != null) {
                avoids.addHost(dest.getHost().getId());
                break;
            }

        }
        return dest;
    }

}
