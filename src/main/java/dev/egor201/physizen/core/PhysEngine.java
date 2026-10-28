package dev.egor201.physizen.core;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.TempAllocatorImpl;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

public final class PhysEngine {

    private static PhysEngine instance;

    private final EngineConfig config;
    private final Logger log;
    private final ConcurrentHashMap<String, PhysWorld> worlds = new ConcurrentHashMap<>();
    private final TempAllocatorImpl tempAllocator;
    private final JobSystemThreadPool jobSystem;
    private final Thread simThread;
    private volatile boolean running = true;
    private final String nativeSource;

    private PhysEngine(EngineConfig config, File dataFolder, Logger log) throws IOException {
        this.config = config;
        this.log = log;
        this.nativeSource = NativeLoader.load(dataFolder, config.nativeVariant, log);
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) {
            throw new IOException("Jolt factory could not be created.");
        }
        Jolt.registerTypes();
        tempAllocator = new TempAllocatorImpl(config.tempAllocatorMegabytes * 1024 * 1024);
        jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, config.workerThreads);
        simThread = new Thread(this::loop, "Physizen-Sim");
        simThread.setDaemon(true);
        simThread.start();
    }

    public static synchronized PhysEngine start(EngineConfig config, File dataFolder, Logger log) throws IOException {
        if (instance == null) {
            instance = new PhysEngine(config, dataFolder, log);
        }
        return instance;
    }

    public static PhysEngine get() {
        return instance;
    }

    public EngineConfig config() {
        return config;
    }

    public String nativeSource() {
        return nativeSource;
    }

    TempAllocatorImpl tempAllocator() {
        return tempAllocator;
    }

    JobSystemThreadPool jobSystem() {
        return jobSystem;
    }

    public PhysWorld createWorld(String id, int stepRate) {
        if (worlds.containsKey(id)) {
            throw new PhysException("Physics world " + id + " already exists.");
        }
        PhysWorld world = new PhysWorld(id, this, log, stepRate > 0 ? stepRate : config.tickRate);
        if (worlds.putIfAbsent(id, world) != null) {
            world.destroy();
            throw new PhysException("Physics world " + id + " already exists.");
        }
        LockSupport.unpark(simThread);
        return world;
    }

    public PhysWorld world(String id) {
        return worlds.get(id);
    }

    public java.util.Collection<PhysWorld> worlds() {
        return worlds.values();
    }

    public boolean removeWorld(String id) {
        PhysWorld world = worlds.remove(id);
        if (world == null) {
            return false;
        }
        world.destroy();
        return true;
    }

    private void loop() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            long elapsed = now - last;
            last = now;
            for (PhysWorld world : worlds.values()) {
                try {
                    world.advance(elapsed);
                }
                catch (Throwable t) {
                    log.warning("Physics world " + world.id + " failed: " + t);
                }
            }
            if (worlds.isEmpty()) {
                LockSupport.parkNanos(20_000_000L);
            }
            else {
                LockSupport.parkNanos(1_000_000L);
            }
        }
    }

    public synchronized void shutdown() {
        running = false;
        LockSupport.unpark(simThread);
        try {
            simThread.join(2000);
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        for (PhysWorld world : worlds.values()) {
            world.destroy();
        }
        worlds.clear();
        instance = null;
    }
}
