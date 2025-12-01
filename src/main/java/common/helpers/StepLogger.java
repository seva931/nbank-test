package common.helpers;

public class StepLogger {

    @FunctionalInterface
    public interface ThrowableRunnable<T> {
        T run() throws Throwable;
    }

    @FunctionalInterface
    public interface ThrowableVoidRunnable {
        void run() throws Throwable;
    }

    public static <T> T log(String title, ThrowableRunnable<T> runnable) {
        // тут можно добавить System.out.println(title), если захочешь лог в консоль
        try {
            return runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static void log(String title, ThrowableVoidRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
