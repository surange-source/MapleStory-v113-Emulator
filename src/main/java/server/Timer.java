package server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Timer {
    private static final Logger LOG = LoggerFactory.getLogger(Timer.class);

    public static class WorldTimer extends Timer {

        private static WorldTimer instance = new WorldTimer();

        private WorldTimer() {
            name = "Worldtimer";
        }

        public static WorldTimer getInstance() {
            return instance;
        }
    }

    public static class MapTimer extends Timer {

        private static MapTimer instance = new MapTimer();

        private MapTimer() {
            name = "Maptimer";
        }

        public static MapTimer getInstance() {
            return instance;
        }
    }

    public static class BuffTimer extends Timer {

        private static BuffTimer instance = new BuffTimer();

        private BuffTimer() {
            name = "Bufftimer";
        }

        public static BuffTimer getInstance() {
            return instance;
        }
    }

    public static class EventTimer extends Timer {

        private static EventTimer instance = new EventTimer();

        private EventTimer() {
            name = "Eventtimer";
        }

        public static EventTimer getInstance() {
            return instance;
        }
    }

    public static class CloneTimer extends Timer {

        private static CloneTimer instance = new CloneTimer();

        private CloneTimer() {
            name = "Clonetimer";
        }

        public static CloneTimer getInstance() {
            return instance;
        }
    }

    public static class EtcTimer extends Timer {

        private static EtcTimer instance = new EtcTimer();

        private EtcTimer() {
            name = "Etctimer";
        }

        public static EtcTimer getInstance() {
            return instance;
        }
    }

    public static class MobTimer extends Timer {

        private static MobTimer instance = new MobTimer();

        private MobTimer() {
            name = "Mobtimer";
        }

        public static MobTimer getInstance() {
            return instance;
        }
    }

    public static class CheatTimer extends Timer {

        private static CheatTimer instance = new CheatTimer();

        private CheatTimer() {
            name = "Cheattimer";
        }

        public static CheatTimer getInstance() {
            return instance;
        }
    }

    public static class PingTimer extends Timer {

        private static PingTimer instance = new PingTimer();

        private PingTimer() {
            name = "Pingtimer";
        }

        public static PingTimer getInstance() {
            return instance;
        }
    }

    private ScheduledExecutorService scheduler;
    private ExecutorService workers;
    protected String file, name;

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        file = "Logs/Log_" + name + "_Except.rtf";
        final String tname = name;
        final ThreadFactory thread =
                new ThreadFactory() {

                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        final Thread t =
                                new Thread(r, tname + "-Worker-" + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                };

        final ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(4, thread);
        stpe.setKeepAliveTime(10, TimeUnit.MINUTES);
        stpe.allowCoreThreadTimeOut(true);
        stpe.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler = stpe;

        workers =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name(tname + "-VT-", 0).factory());
    }

    public void stop() {
        final ScheduledExecutorService s = scheduler;
        final ExecutorService w = workers;
        scheduler = null;
        workers = null;
        if (s == null) {
            LOG.info("Timer not started: {}", getClass().getSimpleName());
            return;
        }
        LOG.info("Timer Stopping: {}", getClass().getSimpleName());
        s.shutdownNow();
        if (w != null) {
            w.shutdownNow();
            try {
                w.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("Timer Stopped: {}", getClass().getSimpleName());
    }

    private Runnable dispatch(final Runnable r) {
        final String logFile = this.file;
        final AtomicBoolean running = new AtomicBoolean(false);
        return () -> {
            final ExecutorService w = workers;
            if (w == null) {
                return;
            }
            if (!running.compareAndSet(false, true)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Timer {} 上一個週期任務尚未完成，跳過本次執行", name);
                }
                return;
            }
            final LoggingSaveRunnable task =
                    new LoggingSaveRunnable(r, logFile) {
                        @Override
                        public void run() {
                            try {
                                super.run();
                            } finally {
                                running.set(false);
                            }
                        }
                    };
            try {
                w.execute(task);
            } catch (RejectedExecutionException e) {
                running.set(false);
                if (w.isShutdown()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Timer {} 已停止，忽略任務提交", name);
                    }
                } else {
                    LOG.warn("Timer {} 提交工作任務失敗", name, e);
                }
            }
        };
    }

    public ScheduledFuture<?> register(Runnable r, long repeatTime, long delay) {
        final ScheduledExecutorService s = scheduler;
        if (s == null) {
            LOG.warn(
                    "Timer {} 尚未啟動，忽略 register 任務",
                    name,
                    new RuntimeException("Timer not started"));
            return null;
        }
        return s.scheduleAtFixedRate(dispatch(r), delay, repeatTime, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> register(Runnable r, long repeatTime) {
        final ScheduledExecutorService s = scheduler;
        if (s == null) {
            LOG.warn(
                    "Timer {} 尚未啟動，忽略 register 任務",
                    name,
                    new RuntimeException("Timer not started"));
            return null;
        }
        return s.scheduleAtFixedRate(dispatch(r), 0, repeatTime, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> schedule(Runnable r, long delay) {
        final ScheduledExecutorService s = scheduler;
        if (s == null) {
            LOG.warn(
                    "Timer {} 尚未啟動，忽略 schedule 任務",
                    name,
                    new RuntimeException("Timer not started"));
            return null;
        }
        return s.schedule(new LoggingSaveRunnable(r, file), delay, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleAsync(Runnable r, long delay) {
        final ScheduledExecutorService s = scheduler;
        if (s == null) {
            LOG.warn(
                    "Timer {} 尚未啟動，忽略 scheduleAsync 任務",
                    name,
                    new RuntimeException("Timer not started"));
            return null;
        }
        return s.schedule(dispatch(r), delay, TimeUnit.MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleAtTimestamp(Runnable r, long timestamp) {
        return schedule(r, timestamp - System.currentTimeMillis());
    }

    private static class LoggingSaveRunnable implements Runnable {

        Runnable r;
        String file;

        public LoggingSaveRunnable(final Runnable r, final String file) {
            this.r = r;
            this.file = file;
        }

        @Override
        public void run() {
            try {
                r.run();
            } catch (Throwable t) {
                LOG.error(file, t);
            }
        }
    }
}
