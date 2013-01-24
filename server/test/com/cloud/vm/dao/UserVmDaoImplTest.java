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
package com.cloud.vm.dao;

import javax.inject.Inject;

import junit.framework.TestCase;

import com.cloud.hypervisor.Hypervisor.HypervisorType;

import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;


public class UserVmDaoImplTest extends TestCase {
    @Inject UserVmDao dao;
     
	public void testPersist() {
        
        dao.expunge(1000l);
        
        UserVmVO vo = new UserVmVO(1000l, "instancename", "displayname", 1, HypervisorType.XenServer, 1, true, true, false, 1, 1, 1, "userdata", "name", null);
        dao.persist(vo);
        
        vo = dao.findById(1000l);
        assert (vo.getType() == VirtualMachine.Type.User) : "Incorrect type " + vo.getType();
    }

}
