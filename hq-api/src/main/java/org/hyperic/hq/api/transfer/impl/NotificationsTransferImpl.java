package org.hyperic.hq.api.transfer.impl;

import java.util.List;

import javax.jms.Destination;

import org.hyperic.hq.api.model.NotificationsReport;
import org.hyperic.hq.api.services.impl.ApiMessageContext;
import org.hyperic.hq.api.transfer.MeasurementTransfer;
import org.hyperic.hq.api.transfer.NotificationsTransfer;
import org.hyperic.hq.api.transfer.ResourceTransfer;
import org.hyperic.hq.api.transfer.mapping.NotificationsMapper;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.notifications.Q;
import org.hyperic.hq.notifications.UnregisteredException;
import org.hyperic.hq.notifications.model.BaseNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("notificationsTransfer")
public class NotificationsTransferImpl implements NotificationsTransfer {
    protected Destination dest;
    @Autowired
    protected NotificationsMapper mapper;
    @Autowired
    protected Q q;
    @Autowired
    protected ResourceTransfer rscTransfer;
    @Autowired
    protected MeasurementTransfer msmtTransfer;
    // will be replaced by the destinations the invokers of this API will pass when registering
    protected Destination dummyDestination = new Destination() {};

    @Transactional (readOnly=true)
    public NotificationsReport poll(ApiMessageContext apiMessageContext) throws UnregisteredException {
        Destination dest = this.dummyDestination;
        if (dest==null) {
            throw new UnregisteredException();
        }
        List<? extends BaseNotification> ns = this.q.poll(dest);
        AuthzSubject subject = apiMessageContext.getAuthzSubject();
        return this.mapper.toNotificationsReport(subject, this.rscTransfer,ns);
    }
    
    @Transactional (readOnly=false)
    public void unregister() {
        this.dest=null;
        this.q.unregister(dest);
        this.rscTransfer.unregister();
        this.msmtTransfer.unregister();
    }
    public Destination getDummyDestination() {
        return this.dummyDestination;
    }
}
