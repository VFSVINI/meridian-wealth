package dev.chronosquant.application.port;

import dev.chronosquant.application.SignalRecord;
import java.util.List;

public interface SignalStore {
    void save(SignalRecord record);

    List<SignalRecord> recent(String symbol, int limit);
}
