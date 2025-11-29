package common.extensions;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TimingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        String testName = context.getRequiredTestClass().getPackageName() + "." + context.getDisplayName();
        START_TIME.set(System.currentTimeMillis());
        System.out.println("Thread " + Thread.currentThread().getName() + ": Test started " + testName);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        String testName = context.getRequiredTestClass().getPackageName() + "." + context.getDisplayName();
        Long start = START_TIME.get();
        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            System.out.println("Thread " + Thread.currentThread().getName() + ": Test finished " + testName +
                    ", test duration " + duration + "ms");
        }
        START_TIME.remove();
    }
}