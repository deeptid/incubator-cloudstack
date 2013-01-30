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
package com.cloud.offerings.dao;

import java.lang.reflect.Field;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Category;
import org.apache.log4j.Logger;

import com.cloud.network.Network;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.Vpc.State;
import com.cloud.network.vpc.VpcVO;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.offering.NetworkOffering.Availability;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.vm.VirtualMachine;
import com.cloud.vpc.dao.MockNetworkOfferingDaoImpl;

@Local(value = VpcDao.class)
@DB(txn = false)
public class MockServiceOfferingDaoImpl extends GenericDaoBase<ServiceOfferingVO, Long> implements ServiceOfferingDao{
    private static final Logger s_logger = Logger.getLogger(MockServiceOfferingDaoImpl.class);

    
    @Override
    public ServiceOfferingVO findByName(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServiceOfferingVO persistSystemServiceOffering(ServiceOfferingVO vo) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ServiceOfferingVO> findPublicServiceOfferings() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ServiceOfferingVO> findServiceOfferingByDomainId(Long domainId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ServiceOfferingVO> findSystemOffering(Long domainId,
            Boolean isSystem, String vm_type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServiceOfferingVO persistDeafultServiceOffering(
            ServiceOfferingVO offering) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ServiceOfferingVO> findDedicatedServiceOfferings(
            Boolean isDedicated) {
        // TODO Auto-generated method stub
        return null;
    }
    
    public ServiceOfferingVO findById(long id) {
        ServiceOfferingVO vo = null;
        if (id == 1) {
            //service offering valid dedicated service offering
            vo = new ServiceOfferingVO("Service Offering For dedicated vm", 1, 512, 500, 0, 0, false, true, null, false, true, null, true, VirtualMachine.Type.User, true);
        } else if (id == 2) {
            //Service offering - invalid value of isdedicated flag
            vo = new ServiceOfferingVO("Service Offering For non-dedicated vm", 1, 512, 500, 0, 0, false, true, null, false, false, null, true, VirtualMachine.Type.User, true); 
        }
        if (vo != null) {
            vo = setId(vo, id);
        }
        return vo;
    }
    
    private ServiceOfferingVO setId(ServiceOfferingVO vo, long id) {
        ServiceOfferingVO voToReturn = vo;
        Class<?> c = voToReturn.getClass();
        try {
            Field f = c.getDeclaredField("id");
            f.setAccessible(true);
            f.setLong(voToReturn, id);
        } catch (NoSuchFieldException ex) {
        s_logger.warn(ex);
           return null;
        } catch (IllegalAccessException ex) {
            s_logger.warn(ex);
            return null;
        }
        
        return voToReturn;
    }

}
