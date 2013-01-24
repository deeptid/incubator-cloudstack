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
package com.cloud.dedicated.dao;


import java.util.List;
import javax.ejb.Local;
import org.apache.log4j.Logger;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Local(value={DedicatedResourceDao.class})
public class DedicatedResourceDaoImpl extends GenericDaoBase<DedicatedResourceVO, Long> implements DedicatedResourceDao {
    private static final Logger s_logger = Logger.getLogger(DedicatedResourceDaoImpl.class);
	
	protected final SearchBuilder<DedicatedResourceVO> ZoneSearch;
	protected final SearchBuilder<DedicatedResourceVO> PodSearch;
	protected final SearchBuilder<DedicatedResourceVO> ClusterSearch;
	
	protected SearchBuilder<DedicatedResourceVO> ListZonesByDomainIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListPodsByDomainIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListClustersByDomainIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListHostsByDomainIdSearch;
	
	protected SearchBuilder<DedicatedResourceVO> ListZonesByAccountIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListPodsByAccountIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListClustersByAccountIdSearch;
	protected SearchBuilder<DedicatedResourceVO> ListHostsByAccountIdSearch;
	
	protected DedicatedResourceDaoImpl() {
	    PodSearch = createSearchBuilder();
        PodSearch.and("pod", PodSearch.entity().getPodId(), SearchCriteria.Op.EQ);
        PodSearch.done();
        
        ZoneSearch = createSearchBuilder();
        ZoneSearch.and("dataCenterId", ZoneSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        ZoneSearch.done();
        
        ClusterSearch = createSearchBuilder();
        ClusterSearch.and("cluster", ClusterSearch.entity().getClusterId(), SearchCriteria.Op.EQ);
        ClusterSearch.done();
        
        ListZonesByDomainIdSearch = createSearchBuilder();
        ListZonesByDomainIdSearch.and("domainId", ListZonesByDomainIdSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        ListZonesByDomainIdSearch.done();
        
        ListZonesByAccountIdSearch = createSearchBuilder();
        ListZonesByAccountIdSearch.and("accountId", ListZonesByAccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        ListZonesByAccountIdSearch.done();
        
        ListPodsByDomainIdSearch = createSearchBuilder();
        ListPodsByDomainIdSearch.and("domainId", ListPodsByDomainIdSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        ListPodsByDomainIdSearch.done();
        
        ListPodsByAccountIdSearch = createSearchBuilder();
        ListPodsByAccountIdSearch.and("accountId", ListPodsByAccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        ListPodsByAccountIdSearch.done();
        
        ListClustersByDomainIdSearch = createSearchBuilder();
        ListClustersByDomainIdSearch.and("domainId", ListClustersByDomainIdSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        ListClustersByDomainIdSearch.done();
        
        ListClustersByAccountIdSearch = createSearchBuilder();
        ListClustersByAccountIdSearch.and("accountId", ListClustersByAccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        ListClustersByAccountIdSearch.done();
        
        ListHostsByDomainIdSearch = createSearchBuilder();
        ListHostsByDomainIdSearch.and("domainId", ListHostsByDomainIdSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        ListHostsByDomainIdSearch.done();
        
        ListHostsByAccountIdSearch = createSearchBuilder();
        ListHostsByAccountIdSearch.and("accountId", ListHostsByAccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        ListHostsByAccountIdSearch.done();
        
        
	}
	
    @Override
    public DedicatedResourceVO findByZoneId(Long zoneId) {
        SearchCriteria<DedicatedResourceVO> sc = ZoneSearch.create();
        sc.setParameters("dataCenterId", zoneId);        
        return findOneBy(sc);
    }
    
    @Override
    public DedicatedResourceVO findByPodId(Long podId) {
        SearchCriteria<DedicatedResourceVO> sc = PodSearch.create();
        sc.setParameters("podId", podId);
        
        return findOneBy(sc);
    }
    
    @Override
    public DedicatedResourceVO findByClusterId(Long clusterId) {
        SearchCriteria<DedicatedResourceVO> sc = PodSearch.create();
        sc.setParameters("clusterId", clusterId);
        
        return findOneBy(sc);
    }
    
    @Override
    public DedicatedResourceVO findByHostId(Long hostId) {
        SearchCriteria<DedicatedResourceVO> sc = PodSearch.create();
        sc.setParameters("hostId", hostId);
        
        return findOneBy(sc);
    }
    
    @Override
    public List<DedicatedResourceVO> findZonesByDomainId(Long domainId){
    	SearchCriteria<DedicatedResourceVO> sc = ListZonesByDomainIdSearch.create();
    	sc.setParameters("domainId", domainId);
        return listBy(sc);    	
    }
    
    @Override
    public List<DedicatedResourceVO> findZonesByAccountId(Long accountId){
    	SearchCriteria<DedicatedResourceVO> sc = ListZonesByAccountIdSearch.create();
    	sc.setParameters("accountId", accountId);
        return listBy(sc);    	
    }
  
    @Override
    public List<DedicatedResourceVO> findPodsByDomainId(Long domainId){
    	SearchCriteria<DedicatedResourceVO> sc = ListPodsByDomainIdSearch.create();
    	sc.setParameters("domainId", domainId);
        return listBy(sc);    	
    }
    
    @Override
    public List<DedicatedResourceVO> findPodsByAccountId(Long accountId){
    	SearchCriteria<DedicatedResourceVO> sc = ListPodsByAccountIdSearch.create();
    	sc.setParameters("accountId", accountId);
        return listBy(sc);    	
    }
    
    @Override
    public List<DedicatedResourceVO> findClustersByDomainId(Long domainId){
    	SearchCriteria<DedicatedResourceVO> sc = ListClustersByDomainIdSearch.create();
    	sc.setParameters("domainId", domainId);
        return listBy(sc);    	
    }
    
    @Override
    public List<DedicatedResourceVO> findHostsByAccountId(Long accountId){
    	SearchCriteria<DedicatedResourceVO> sc = ListHostsByAccountIdSearch.create();
    	sc.setParameters("accountId", accountId);
        return listBy(sc);    	
    }
    
}
