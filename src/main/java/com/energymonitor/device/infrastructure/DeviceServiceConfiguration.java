package com.energymonitor.device.infrastructure;

import com.energymonitor.device.application.port.in.RecordDeviceConnectivity;
import com.energymonitor.device.application.port.out.DeviceEventPort;
import com.energymonitor.device.application.port.out.DevicePersistencePort;
import com.energymonitor.device.application.port.out.DeviceStatusLogPersistencePort;
import com.energymonitor.device.application.port.out.IdentifierGeneratorPort;
import com.energymonitor.device.application.usecase.BrokerAccessService;
import com.energymonitor.device.application.usecase.DeviceConnectivityService;
import com.energymonitor.device.application.usecase.DeviceReportService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Beans of the use cases that carry no Spring annotation, and the scheduling of the inactivity
 * check.
 */
@Configuration
@EnableScheduling
public class DeviceServiceConfiguration {

    /**
     * The connectivity use case, wrapped so every call is one transaction: the status row and its
     * outbox message commit together.
     */
    @Bean
    public TransactionalDeviceConnectivity deviceConnectivity(
            DeviceStatusLogPersistencePort statusLogs,
            IdentifierGeneratorPort identifiers,
            DeviceEventPort events,
            Clock clock,
            PlatformTransactionManager transactions,
            @Value("${device.connectivity.offline-after:PT150S}") Duration offlineAfter) {
        return new TransactionalDeviceConnectivity(
                new DeviceConnectivityService(statusLogs, identifiers, events, clock, offlineAfter),
                new TransactionTemplate(transactions));
    }

    @Bean
    public DeviceReportService deviceReportService(DevicePersistencePort devices,
                                                   RecordDeviceConnectivity connectivity) {
        return new DeviceReportService(devices, connectivity);
    }

    /**
     * This service's broker credentials are the ones it connects with ({@code mqtt.username} /
     * {@code mqtt.password}), so they are configured once; energy-monitor-backend's are under
     * {@code mqtt.broker-auth.backend-*} and the measurement service's under
     * {@code mqtt.broker-auth.measurement-*}, the same values each is given to connect.
     */
    @Bean
    public BrokerAccessService brokerAccessService(
            DevicePersistencePort devices,
            @Value("${mqtt.username:}") String serviceUsername,
            @Value("${mqtt.password:}") String servicePassword,
            @Value("${mqtt.broker-auth.backend-username:}") String backendUsername,
            @Value("${mqtt.broker-auth.backend-password:}") String backendPassword,
            @Value("${mqtt.broker-auth.measurement-username:}") String measurementUsername,
            @Value("${mqtt.broker-auth.measurement-password:}") String measurementPassword) {
        return new BrokerAccessService(devices, serviceUsername, servicePassword,
                backendUsername, backendPassword, measurementUsername, measurementPassword);
    }
}
