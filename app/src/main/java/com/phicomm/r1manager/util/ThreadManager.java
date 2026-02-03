package com.phicomm.r1manager.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadManager {
    private static volatile ThreadManager instance;
    private final ExecutorService ioExecutor;
    private final ExecutorService audioExecutor;

    private ThreadManager() {
        // For IO/Network tasks (Cached pool grows as needed)
        this.ioExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("IO-Thread-"));

        // For Audio specific tasks that might need isolation or higher priority
        // Using cached pool as well, but logically separated
        this.audioExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("Audio-Thread-"));
    }

    public static ThreadManager getInstance() {
        if (instance == null) {
            synchronized (ThreadManager.class) {
                if (instance == null) {
                    instance = new ThreadManager();
                }
            }
        }
        return instance;
    }

    public void executeIO(Runnable task) {
        ioExecutor.execute(task);
    }

    public void executeAudio(Runnable task) {
        audioExecutor.execute(task);
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
