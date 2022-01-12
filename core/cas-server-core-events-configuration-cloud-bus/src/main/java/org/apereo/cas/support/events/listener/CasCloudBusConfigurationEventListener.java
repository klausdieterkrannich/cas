package org.apereo.cas.support.events.listener;

import org.apereo.cas.util.spring.CasEventListener;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;

/**
 * Interface for {@code DefaultCasCloudBusConfigurationEventListener} to allow spring {@code @Async} support to use JDK proxy.
 * @author Hal Deadman
 * @since 6.5.0
 */
public interface CasCloudBusConfigurationEventListener extends CasEventListener {

    /**
     * Handle refresh event when issued by the cloud bus.
     *
     * @param event the event
     */
    void handleRefreshEvent(RefreshRemoteApplicationEvent event);
}