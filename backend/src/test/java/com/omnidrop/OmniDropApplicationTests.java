package com.omnidrop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "server.port=0",
        "omnidrop.network.tcp-port=19090",
        "omnidrop.network.udp-port=18888",
        "omnidrop.storage.downloads-dir=target/test-downloads",
        "omnidrop.storage.checkpoints-dir=target/test-checkpoints"
})
class OmniDropApplicationTests {

    @Test
    @DisplayName("Spring Application Context should load successfully")
    void contextLoads() {
    }
}
