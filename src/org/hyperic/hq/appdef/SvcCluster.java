package org.hyperic.hq.appdef;

import java.util.Collection;

/**
 *
 */
public class SvcCluster extends AppdefBean
{
    private String name;
    private String sortName;
    private String description;
    private Integer groupId;
    private ServiceType serviceType;
    private Collection appServices;
    private Collection services;

    // Constructors

    /**
     * default constructor
     */
    public SvcCluster()
    {
        super();
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getSortName()
    {
        return this.sortName;
    }

    public void setSortName(String sortName)
    {
        this.sortName = sortName;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Integer getGroupId()
    {
        return this.groupId;
    }

    public void setGroupId(Integer groupId)
    {
        this.groupId = groupId;
    }

    public ServiceType getServiceType()
    {
        return this.serviceType;
    }

    public void setServiceType(ServiceType serviceType)
    {
        this.serviceType = serviceType;
    }

    public Collection getAppServices()
    {
        return this.appServices;
    }

    public void setAppServices(Collection appServices)
    {
        this.appServices = appServices;
    }

    public Collection getServices()
    {
        return this.services;
    }

    public void setServices(Collection services)
    {
        this.services = services;
    }

    // fix equals and hashCode
    public boolean equals(Object other)
    {
        if ((this == other)) return true;
        if ((other == null)) return false;
        if (!(other instanceof SvcCluster)) return false;
        SvcCluster castOther = (SvcCluster) other;

        return ((this.getName() == castOther.getName()) || (this.getName() != null && castOther.getName() != null && this.getName().equals(castOther.getName())));
    }

    public int hashCode()
    {
        int result = 17;
        return result;
    }
}
