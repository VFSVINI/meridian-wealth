package dev.meridianwealth.application;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Alterna entre cotação real/cache pura e fluxo simulado contínuo sobre a última cotação real. */
@Component
public class StreamSettings {
    private final AtomicBoolean flow;

    public StreamSettings(@Value("${meridianwealth.stream.flow-default:true}") boolean flowDefault) {
        this.flow = new AtomicBoolean(flowDefault);
    }

    public boolean flow() { return flow.get(); }

    public void setFlow(boolean enabled) { flow.set(enabled); }
}
