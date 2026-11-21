package dev.meridianwealth.application.port;

import dev.meridianwealth.application.SignalRecord;
import java.util.List;

public interface SignalStore {
    void save(SignalRecord record);

    List<SignalRecord> recent(String symbol, int limit);
}
