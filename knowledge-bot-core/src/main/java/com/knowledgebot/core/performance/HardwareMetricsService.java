package com.knowledgebot.core.performance;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

@Service
public class HardwareMetricsService {

    private static final Logger log = LoggerFactory.getLogger(HardwareMetricsService.class);

    // Thresholds
    private static final double CRITICAL_RAM_THRESHOLD = 0.15; // 15% free RAM left

    private OperatingSystemMXBean osBean;
    private int totalCores;
    private long totalRamBytes;

    @PostConstruct
    public void init() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.totalCores = Runtime.getRuntime().availableProcessors();
        this.totalRamBytes = osBean.getTotalMemorySize();

        log.info("Hardware Introspection Initialized: {} CPU Cores, {} GB Total RAM",
                totalCores, totalRamBytes / (1024 * 1024 * 1024));
    }

    /**
     * Calculates the percentage of physical RAM currently available on the host machine.
     */
    public double getFreeRamPercentage() {
        long freeRam = osBean.getFreeMemorySize();
        return (double) freeRam / totalRamBytes;
    }

    /**
     * True if the system is running dangerously low on RAM.
     */
    public boolean isRamCritical() {
        boolean critical = getFreeRamPercentage() < CRITICAL_RAM_THRESHOLD;
        if (critical) {
            log.warn("CRITICAL HARDWARE STATE: System RAM is running low! Free RAM: {}%",
                    Math.round(getFreeRamPercentage() * 100));
        }
        return critical;
    }

    // Note: VRAM checking typically requires native bindings (like JNI or executing `nvidia-smi`).
    // If you add GPU support later, you would expand this service to execute a process and parse VRAM stats.
}