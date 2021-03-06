package com.oracle.qa.dataload.service.async;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimedCompletables {

    public static Executor timed(ExecutorService executorService, Duration duration) {
        return new TimeOutExecutorService(executorService, duration);
    }

    static class TimeOutExecutorService extends CompletableExecutors.DelegatingCompletableExecutorService {
        private final Duration timeout;
        private final ScheduledExecutorService schedulerExecutor;

        TimeOutExecutorService(ExecutorService delegate, Duration timeout) {
            super(delegate);
            this.timeout = timeout;
            schedulerExecutor = Executors.newScheduledThreadPool(1);
        }

        @Override public <T> CompletableFuture<T> submit(Callable<T> task) {
            CompletableFuture<T> cf = new CompletableFuture<>();
            Future<?> future = delegate.submit(() -> {
                try {
                    cf.complete(task.call());
                } catch (CancellationException e) {
                    cf.cancel(true);
                } catch (Throwable ex) {
                    cf.completeExceptionally(ex);
                }
            });

            schedulerExecutor.schedule(() -> {
                if (!cf.isDone()) {
                    cf.completeExceptionally(new TimeoutException("Timeout after " + timeout));
                    future.cancel(true);
                }
            }, timeout.toMillis(), TimeUnit.MILLISECONDS);
            return cf;
        }
    }
}