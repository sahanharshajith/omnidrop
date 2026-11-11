package com.omnidrop;

import com.omnidrop.config.OmniDropProperties;
import com.omnidrop.discovery.UdpDiscoveryService;
import com.omnidrop.receiver.TcpFileReceiverService;
import com.omnidrop.utils.NetworkInterfaceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.InetAddress;
import java.util.List;

/**
 * OmniDrop Backend Application Entry Point.
 * 
 * Initializes the Spring Boot application context, launches the background
 * UDP discovery listener (Member 1) and TCP file receiver ServerSocket (Member 3).
 */
@SpringBootApplication
@EnableConfigurationProperties(OmniDropProperties.class)
public class OmniDropApplication {

    private static final Logger log = LoggerFactory.getLogger(OmniDropApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(OmniDropApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupRunner(OmniDropProperties properties,
                                           UdpDiscoveryService discoveryService,
                                           TcpFileReceiverService receiverService) {
        return args -> {
            log.info("==================================================================");
            log.info("  Starting OmniDrop P2P Offline File Sharing Node");
            log.info("  Node Name : {}", properties.getNode().getName());
            log.info("  Node ID   : {}", properties.getNode().getId());
            log.info("  TCP Port  : {}", properties.getNetwork().getTcpPort());
            log.info("  UDP Port  : {}", properties.getNetwork().getUdpPort());
            log.info("==================================================================");

            List<InetAddress> localAddresses = NetworkInterfaceUtils.getLocalIPv4Addresses();
            log.info("Detected local IPv4 network interfaces:");
            for (InetAddress addr : localAddresses) {
                log.info("  -> {} ({})", addr.getHostAddress(), addr.getHostName());
            }

            // Start TCP ServerSocket receiver daemon (Member 3)
            receiverService.startServer();

            // Start UDP Discovery beacon broadcaster and listener daemons (Member 1)
            discoveryService.start();

            log.info("OmniDrop node is fully operational and listening on local network.");
        };
    }
}
