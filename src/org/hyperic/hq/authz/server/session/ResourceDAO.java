/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.authz.server.session;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.dao.DAOFactory;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.AppdefUtil;
import org.hyperic.hq.authz.shared.AuthzConstants;
import org.hyperic.hq.authz.shared.AuthzSubjectValue;
import org.hyperic.hq.authz.shared.ResourceTypeValue;
import org.hyperic.hq.authz.shared.ResourceValue;
import org.hyperic.hq.dao.HibernateDAO;
import org.hibernate.Query;

/**
 * CRUD methods, finders, etc. for Resource
 */
public class ResourceDAO extends HibernateDAO
{
    Log log = LogFactory.getLog(ResourceDAO.class);

    public ResourceDAO(DAOFactory f) { 
        super(Resource.class, f);
    }

    public Resource create(AuthzSubject creator, ResourceValue createInfo) {
        /* set resource type */
        ResourceTypeValue typeValue = createInfo.getResourceTypeValue();

        if (typeValue == null) {
            // XXX - decide what exception to throw here
            // throw new CreateException("Null resourceType given.");
            throw new IllegalArgumentException(
                "ResourceTypevValue is not defined");
        }
        Resource resource = new Resource(createInfo);

        ResourceType resType = DAOFactory.getDAOFactory().getResourceTypeDAO()
            .findById(typeValue.getId());
        resource.setResourceType(resType);

        /* set owner */
        AuthzSubjectValue ownerValue = createInfo.getAuthzSubjectValue();
        if (ownerValue != null) {
            creator = DAOFactory.getDAOFactory().getAuthzSubjectDAO() 
                .findById(ownerValue.getId());
        }
        resource.setOwner(creator);
        save(resource);

        /* add it to the root resourcegroup */
        /* This is done as the overlord, since it is meant to be an
           anonymous, priviledged operation */
        ResourceGroup authzGroup = 
            DAOFactory.getDAOFactory().getResourceGroupDAO() 
            .findByName(AuthzConstants.rootResourceGroupName);
        if (authzGroup == null) {
            throw new IllegalArgumentException("can not find Resource Group: "+
                                               AuthzConstants.rootResourceGroupName);
        }
        authzGroup.addResource(resource);

        return resource;
    }

    public Resource findById(Integer id) {
        return (Resource) super.findById(id);
    }

    public void save(Resource entity) {
        super.save(entity);
    }

    public Resource merge(Resource entity) {
        return (Resource) super.merge(entity);
    }

    public void remove(Resource entity) {
        // remove resource from all resourceGroups
        ResourceGroupDAO dao = DAOFactory.getDAOFactory().getResourceGroupDAO();
        for(Iterator i=entity.getResourceGroups().iterator(); i.hasNext();) {
            ResourceGroup rg = (ResourceGroup)i.next();
            ResourceGroup resourceGroup =
                dao.findById(rg.getId());
            resourceGroup.getResourceSet().remove(entity);
        }
        entity.getResourceGroups().clear();
        super.remove(entity);
    }

    public void evict(Resource entity) {
        super.evict(entity);
    }

    public boolean isOwner(Resource entity, Integer possibleOwner) {
        boolean is = false;

        if (possibleOwner == null) {
            log.error("possible Owner is NULL. " +
                    "This is probably not what you want.");
            /* XXX throw exception instead */
        } else {
            /* overlord owns every thing */
            if (is = possibleOwner.equals(AuthzConstants.overlordId)
                    == false) {
                if (log.isDebugEnabled() && possibleOwner != null) {
                    log.debug("User is " + possibleOwner +
                              " owner is " + entity.getOwner().getId());
                }
                is = (possibleOwner.equals(entity.getOwner().getId()));
            }
        }
        return is;
    }

    private Map groupByAuthzType(AppdefEntityID[] ids) {

        HashMap m = new HashMap();
        for (int i = 0; i < ids.length; i++) {
            String type =
                AppdefUtil.appdefTypeIdToAuthzTypeStr(ids[i].getType());
            ArrayList idList = (ArrayList)m.get(type);
            if (idList == null) {
                idList = new ArrayList();
                m.put(type, idList);
            }
            idList.add(ids[i].getId());
        }
        return m;
    }

    private int deleteResourceObject(AppdefEntityID[] ids, String object,
                                     String col)
    {
        Map map = groupByAuthzType(ids);
        StringBuffer sql = new StringBuffer()
            .append("delete ")
            .append(object)
            .append(" where ");
        for (int i = 0; i < map.size(); i++) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append(col)
                .append(" in (")
                .append("select r.id from Resource r, " +
                        "ResourceType rt where r.resourceType.id=rt.id and ")
                .append("rt.name = :rtname" + i + " and " )
                .append("r.instanceId in (:list" +  i + ") ")
                .append(") ");
        }
        int j = 0;
        Query q = getSession().createQuery(sql.toString());
        for (Iterator i = map.keySet().iterator(); i.hasNext(); j++) {
            String rtname = (String)i.next();
            List list = (List)map.get(rtname);
            q.setString("rtname" + j, rtname)
                .setParameterList("list" + j, list);
        }
        return q.executeUpdate();
    }

    public int deleteByInstances(AppdefEntityID[] ids) {
        // kludge to work around hiberate's limitation to define
        // on-delete="cascade" on many-to-many relationships
        deleteResourceObject(ids, "ResGrpResMap", "id.resource.id");
        return deleteResourceObject(ids, "Resource", "id");
    }

    public Resource findByInstanceId(ResourceType type, Integer id) {
        return findByInstanceId(type.getId(), id);
    }
    
    public Resource findByInstanceId(Integer typeId, Integer id) {            
        String sql = "from Resource where instanceId = ? and" +
                     " resourceType.id = ?";
        return (Resource)getSession().createQuery(sql)
            .setInteger(0, id.intValue())
            .setInteger(1, typeId.intValue())
            .uniqueResult();
    }
    
    public Collection findByOwner(AuthzSubject owner) {
        String sql = "from Resource where owner.id = ?";
        return getSession().createQuery(sql)
                .setInteger(0, owner.getId().intValue())
                .list();
    }
    
    public Collection findByOwnerAndType(AuthzSubject owner,
                                         ResourceType type ) {
        String sql = "from Resource where owner.id = ? and resourceType.id = ?";
        return getSession().createQuery(sql)
            .setInteger(0, owner.getId().intValue())
            .setInteger(1, type.getId().intValue())
            .list();
    }

    public Collection findViewableSvcRes_orderName(Integer user,
                                                   Boolean fSystem)
    {
        // we use join here to produce a single
        // join => the strategy here is to rely on
        // the database query optimizer to optimize the query
        // by feeding it a single query.
        //
        // The important point is we should first give the
        // opportunity to the database to do the "query" optimization
        // before we do anything else.
        // Note: this should be refactored to use named queries so
        // that we can perform "fetch" optimization outside of the code
        String sql = "select distinct r from Resource r " +
                     " join r.resourceGroups rg " +
                     " join rg.roles role " +
                     " join role.subjects subj " +
                     " join role.operations op " +
                     " join r.resourceType rt " +
                     "where " +
                     "  r.system = :system and " +
                     "  (subj.id = :subjId or r.owner.id = :subjId) and " +
                     "  (" +
                     "   rt.name = 'covalentEAMService' or " +
                     "   rt.name = 'covalentAuthzResourceGroup') and " +
                     "  (" +
                     "   op.name = 'viewService' or " +
                     "   op.name = 'viewResourceGroup') and " +
                     "   0 = (select count(*) from Resource r2 " +
                     "        join r2.resourceGroups rg2 " +
                     "        where r.id = r2.id and rg2.groupType = 15 and" +
                     "              rg2.clusterId != -1) " +
                     "order by r.sortName ";
        List resources =
            getSession().createQuery(sql)
                        .setBoolean("system", fSystem.booleanValue())
                        .setInteger("subjId", user.intValue())
                        .list();

        // Hibernate's distinct does not work well with joins - do filter here
        Integer lastId = null; // Track the last one we looked at
        for (Iterator it = resources.iterator(); it.hasNext();) {
            Resource res = (Resource) it.next();
            if (res.getId().equals(lastId)) {
                it.remove();
            } else {
                lastId = res.getId();
            }
        }

        return resources;
    }

    public Collection findSvcRes_orderName(Boolean fSystem)
    {
        String sql="select distinct r from Resource r " +
                   " join r.resourceGroups rg " +
                   " join rg.roles role " +
                   " join role.operations op " +
                   " join r.resourceType rt " +
                   "where " +
                   "  r.system = ? and " +
                   "  (" +
                   "   rt.name = 'covalentEAMService' or " +
                   "   rt.name = 'covalentAuthzResourceGroup') and " +
                   "  (" +
                   "   op.name = 'viewService' or " +
                   "   op.name = 'viewResourceGroup') and " +
                   "   0 = (select count(*) from Resource r2 " +
                   "        join r2.resourceGroups rg2 " +
                   "        where r.id = r2.id and rg2.groupType = 15 and" +
                   "              rg2.clusterId != -1) " +
                   "order by r.sortName ";
        
        List resources =
            getSession().createQuery(sql)
                        .setBoolean(0, fSystem.booleanValue())
                        .list();
        
        return resources;
    }

    public Collection findInGroupAuthz_orderName(Integer userId,
                                                 Integer groupId,
                                                 Boolean fSystem)
    {
        String sql="select distinct r from Resource r " +
                   " join r.resourceGroups rgg" +
                   " join r.resourceGroups rg " +
                   " join rg.roles role " +
                   " join role.subjects subj " +
                   " join role.operations op " +
                   "where " +
                   " r.system = :system and " +
                   " rgg.id = :groupId and " +
                   " (subj.id = :subjectId or " +
                   "  r.owner.id = :subjectId or " +
                   "  subj.authDsn = 'covalentAuthzInternalDsn') and " +
                   " op.resourceType.id = r.resourceType.id and " +
                   " (" +
                   "  op.name = 'viewPlatform' or " +
                   "  op.name = 'viewServer' or " +
                   "  op.name = 'viewService' or " +
                   "  op.name = 'viewApplication' or " +
                   "  op.name = 'viewApplication' or " +
                   "  (op.name='viewResourceGroup' and " +
                   "    not r.instanceId = :groupId) )" +
                   " order by r.sortName ";
        return getSession().createQuery(sql)
            .setBoolean("system", fSystem.booleanValue())
            .setInteger("groupId", groupId.intValue())
            .setInteger("subjectId", userId.intValue())
            .list();
    }

    public Collection findInGroup_orderName(Integer groupId,
                                            Boolean fSystem)
    {
        String sql="select distinct r from Resource r " +
                   " join r.resourceGroups rgg" +
                   " join r.resourceGroups rg " +
                   " join rg.roles role " +
                   " join role.subjects subj " +
                   " join role.operations op " +
                   "where " +
                   " r.system = :system and " +
                   " rgg.id = :groupId and " +
                   " (subj.id=1 or r.owner.id=1 or " +
                   "  subj.authDsn = 'covalentAuthzInternalDsn') and " +
                   " op.resourceType.id = r.resourceType.id and " +
                   " (op.name = 'viewPlatform' or " +
                   "  op.name = 'viewServer' or " +
                   "  op.name = 'viewService' or " +
                   "  op.name = 'viewApplication' or " +
                   "  op.name = 'viewApplication' or " +
                   "  (op.name='viewResourceGroup' and " +
                   "    not r.instanceId = :groupId) )" +
                   " order by r.sortName ";

        return getSession().createQuery(sql)
            .setBoolean("system", fSystem.booleanValue())
            .setInteger("groupId", groupId.intValue())
            .list();
    }

    public Collection findScopeByOperationBatch(AuthzSubject subjLoc,
                                                Resource[] resLocArr,
                                                Operation[] opLocArr)
    {
        StringBuffer sb = new StringBuffer();

        sb.append ("SELECT DISTINCT r " )
            .append ( "FROM Resource r      " )
            .append ( "  join r.resourceGroups g" )
            .append ( "  join g.roles e         " )
            .append ( "  join e.operations o    " )
            .append ( "  join e.subjects s       " )
            .append ( "    WHERE s.id = ?         " )
            .append ( "          AND (          " );

        for (int x=0; x< resLocArr.length ; x++) {
            if (x>0) sb.append(" OR ");
            sb.append(" (o.id=")
                .append(opLocArr[x].getId())
                .append(" AND r.id=")
                .append(resLocArr[x].getId())
                .append(") ");
        }
        sb.append(")");
        return getSession().createQuery(sb.toString())
            .setInteger(0, subjLoc.getId().intValue())
            .list();
    }
}
