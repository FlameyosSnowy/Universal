package io.github.flameyossnowy.universal.api;

public interface ReadPolicy {
    boolean bypassCache();

    boolean allowStale();

    ReadPolicy NO_READ_POLICY = new ReadPolicy() {
        @Override
        public boolean bypassCache() {
            return false;
        }

        @Override
        public boolean allowStale() {
            return true;
        }
    };

    ReadPolicy STRONG_READ_POLICY = new ReadPolicy() {
        @Override
        public boolean bypassCache() {
            return true;
        }

        @Override
        public boolean allowStale() {
            return false;
        }
    };

    ReadPolicy EVENTUAL_READ_POLICY = new ReadPolicy() {
        @Override
        public boolean bypassCache() {
            return false;
        }

        @Override
        public boolean allowStale() {
            return true;
        }
    };
}