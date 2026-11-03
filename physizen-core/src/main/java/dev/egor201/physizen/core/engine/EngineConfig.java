package dev.egor201.physizen.core.engine;

public final class EngineConfig {

    public int workerThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    public int tempAllocatorMegabytes = 16;
    public int maxBodies = 65536;
    public int maxBodiesPerWorld = 20000;
    public int maxConstraintsPerWorld = 4096;
    public int maxCharactersPerWorld = 256;
    public int maxBodyPairs = 65536;
    public int maxContactConstraints = 20480;
    public int tickRate = 60;
    public int maxCatchupSteps = 4;
    public int collisionSteps = 1;
    public int contactEventQueueLimit = 4096;
    public int contactPersistIntervalSteps = 6;
    public int sensorStayIntervalSteps = 0;
    public int maxCompoundChildren = 256;
    public int maxInternalCompoundChildren = 8192;
    public double fallOutY = -128.0;
    public double contactMinSpeed = 0.5;
    public int exactContactsPerStep = 8;
    public double ragdollHitMinSpeed = 2.0;
    public double vehicleHitMinSpeed = 3.0;
    public String nativeVariant = "auto";
}
