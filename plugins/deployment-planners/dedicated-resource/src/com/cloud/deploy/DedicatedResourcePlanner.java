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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.dc.DedicatedResourceVO;
import com.cloud.dc.HostPodVO;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.offering.ServiceOffering;
import com.cloud.user.AccountVO;
import com.cloud.utils.Pair;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

@Local(value=DeploymentPlanner.class)
public class DedicatedResourcePlanner extends FirstFitPlanner implements DeploymentPlanner {

    private static final Logger s_logger = Logger.getLogger(DedicatedResourcePlanner.class);

    /**
     * This method should reorder the given list of Cluster Ids by applying any necessary heuristic 
     * for this planner
     * @return List<Long> ordered list of Cluster Ids
     */
    @Override
    protected List<Long> reorderClusters(long id, boolean isZone, Pair<List<Long>, Map<Long, Double>> clusterCapacityInfo, VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan){
        List<Long> clusterIdsByCapacity = clusterCapacityInfo.first();
        if(vmProfile.getOwner() == null || !isZone){
            return clusterIdsByCapacity;
        }
        return applyDedicatedResourceHeuristicToClusters(vmProfile, plan, clusterIdsByCapacity, vmProfile.getOwner().getAccountId());
    }

    private List<Long> applyDedicatedResourceHeuristicToClusters(VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan, List<Long> prioritizedClusterIds, long accountId){
        //user has VMs in certain pods. - prioritize those pods first
        // User has dedicated pods and clusters
        // If Service Offering Dedication Flag is ON, use Implicitly dedicated resources,
        // If VM Dedication Flag is ON, use explicitly dedicated resources
        List<Long> clusterList = new ArrayList<Long>();
        List<Long> podIds = new ArrayList<Long>();
        ServiceOffering offering = vmProfile.getServiceOffering();
        boolean implicit = offering.getImplicitDedication();
        VirtualMachine vm = vmProfile.getVirtualMachine();
        List<Long> dedicatedPodIds = listDedicatedPods(accountId, podIds, implicit, vm.getUseDedication());

        if(!podIds.isEmpty()){
            clusterList = reorderClustersByDedicatedPods(prioritizedClusterIds, dedicatedPodIds);
        }else{
            clusterList = reorderDedicatedClusters(prioritizedClusterIds, accountId, implicit, vm.getUseDedication());
        }
        return clusterList;
    }    

    private List<Long> reorderDedicatedClusters(List<Long> prioritizedClusterIds, long accountId, boolean implicit, boolean explicit) {
        List<DedicatedResourceVO> dedicatedClusters = new ArrayList<DedicatedResourceVO>();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Searching dedicated Clusters for account "+ accountId);
        }
        if (implicit) {
            dedicatedClusters = _dedicatedDao.findClustersByImplicitDedication(implicit);

        } else if (explicit) {
            dedicatedClusters = _dedicatedDao.findClustersByAccountId(accountId);
            //also add dedicated clusters of domain to which account belongs
            AccountVO account = _accountDao.findById(accountId);
            long domainId = account.getDomainId(); 
            if (_domainDao.findById(domainId) != null) {
                List<DedicatedResourceVO> dedicatedClustersByDomain = _dedicatedDao.findClustersByDomainId(domainId);
                for (DedicatedResourceVO dedicatedCluster : dedicatedClustersByDomain) {
                    prioritizedClusterIds.add(dedicatedCluster.getClusterId());
                }
            }
        } else {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("No dedicated Clusters found for account "+ accountId);
            }
        }
        for (DedicatedResourceVO dedicatedCluster : dedicatedClusters){
            prioritizedClusterIds.add(dedicatedCluster.getClusterId());
        }
        return prioritizedClusterIds;
    }

    private List<Long> reorderClustersByDedicatedPods(
            List<Long> clusterIds, List<Long> podIds) {
        List<Long> reorderedClusters = new ArrayList<Long>();
        Map<Long, List<Long>> podClusterMap = _clusterDao.getPodClusterIdMap(clusterIds);
        for (Long podId : podIds){
            HostPodVO pod = _podDao.findById(podId);
            List<Long> clustersOfThisPod = podClusterMap.get(pod);
            if(clustersOfThisPod != null){
                for(Long clusterId : clustersOfThisPod){
                    reorderedClusters.add(clusterId);
                }
            }
        }

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Reordered cluster list: " + reorderedClusters);
        }
        return reorderedClusters;
    }

    private List<Long> listDedicatedPods(long accountId, List<Long> podIds, boolean implicit, boolean explicit) {
        List<DedicatedResourceVO> dedicatedPods = new ArrayList<DedicatedResourceVO>();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Searching dedicated Pods for account "+ accountId);
        }

        if (implicit) {
            dedicatedPods = _dedicatedDao.findPodsByImplicitDedication(implicit);
        } else if (explicit) {
            dedicatedPods = _dedicatedDao.findPodsByAccountId(accountId);
            //also add dedicated clusters of domain to which account belongs
            AccountVO account = _accountDao.findById(accountId);
            long domainId = account.getDomainId(); 
            if (_domainDao.findById(domainId) != null) {
                List<DedicatedResourceVO> dedicatedPodsByDomain = _dedicatedDao.findClustersByDomainId(domainId);
                for (DedicatedResourceVO dedicatedPod : dedicatedPodsByDomain) {
                    podIds.add(dedicatedPod.getPodId());
                }
            }

        } else  {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("No dedicated Pods found for account "+ accountId);
            }
        }
        for (DedicatedResourceVO dedicatedPod : dedicatedPods){
            podIds.add(dedicatedPod.getPodId());
        }
        return podIds;
    }


    /**
     * This method should reorder the given list of Pod Ids by applying any necessary heuristic 
     * for this planner
     * For DedicatedResourcePlanner we need to order the pods that are dedicated and user needs dedication 
     * @return List<Long> ordered list of Pod Ids
     */
    @Override
    protected List<Long> reorderPods(Pair<List<Long>, Map<Long, Double>> podCapacityInfo, VirtualMachineProfile<? extends VirtualMachine> vmProfile, DeploymentPlan plan){
        List<Long> podIdsByCapacity = podCapacityInfo.first();
        if(vmProfile.getOwner() == null){
            return podIdsByCapacity;
        }
        long accountId = vmProfile.getOwner().getAccountId(); 
        VirtualMachine vm = vmProfile.getVirtualMachine();
        ServiceOffering offering = vmProfile.getServiceOffering();
        boolean implicit = offering.getImplicitDedication();
        boolean explicit = vm.getUseDedication();
        List<Long> podIds = new ArrayList<Long>();

        //Filter out non-dedicated resources
        podIds = listDedicatedPods(accountId, podIds, implicit, explicit);
        if(!podIds.isEmpty()){
            //remove pods that are not dedicated
            podIds.retainAll(podIdsByCapacity);
            podIdsByCapacity.removeAll(podIds);
            podIds.addAll(podIdsByCapacity);
            return podIds;
        }else{
            return podIdsByCapacity;
        }
    }

    @Override
    public boolean canHandle(VirtualMachineProfile<? extends VirtualMachine> vm, DeploymentPlan plan, ExcludeList avoid) {
        ServiceOffering offering = vm.getServiceOffering();
        VirtualMachine virtualMachine = vm.getVirtualMachine();
        if(vm.getHypervisorType() != HypervisorType.BareMetal){
            //check the allocation strategy
            if (offering.getImplicitDedication() || virtualMachine.getUseDedication()){
                return true;
            }
        }
        return false;
    }

}
