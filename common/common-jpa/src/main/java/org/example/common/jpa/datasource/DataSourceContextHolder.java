package org.example.common.jpa.datasource;

public final class DataSourceContextHolder {

    private static final ThreadLocal<Integer> READ_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DataSourceContextHolder() {
    }

    public static ReadScope read() {
        READ_DEPTH.set(READ_DEPTH.get() + 1);
        return new ReadScope();
    }

    public static boolean isRead() {
        return READ_DEPTH.get() > 0;
    }

    public static void clear() {
        READ_DEPTH.remove();
    }

    private static void exitRead() {
        int depth = READ_DEPTH.get();

        if (depth <= 1) {
            READ_DEPTH.remove();
            return;
        }

        READ_DEPTH.set(depth - 1);
    }

    public static final class ReadScope implements AutoCloseable {

        private boolean closed;

        private ReadScope() {
        }

        @Override
        public void close() {
            if (!closed) {
                DataSourceContextHolder.exitRead();
                closed = true;
            }
        }
    }
}
