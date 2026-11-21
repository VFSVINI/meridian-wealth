CREATE TABLE candles (
    symbol  VARCHAR(32)      NOT NULL,
    ts      BIGINT           NOT NULL,
    open    DOUBLE PRECISION NOT NULL,
    high    DOUBLE PRECISION NOT NULL,
    low     DOUBLE PRECISION NOT NULL,
    close   DOUBLE PRECISION NOT NULL,
    volume  DOUBLE PRECISION NOT NULL,
    source  VARCHAR(32)      NOT NULL,
    PRIMARY KEY (symbol, ts)
);

CREATE TABLE signals (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol      VARCHAR(32)      NOT NULL,
    ts          BIGINT           NOT NULL,
    score       INT              NOT NULL,
    signal      VARCHAR(32)      NOT NULL,
    phase       VARCHAR(32)      NOT NULL,
    regime      VARCHAR(16)      NOT NULL,
    confidence  INT              NOT NULL,
    risk        VARCHAR(16)      NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_signals_symbol_ts ON signals (symbol, ts DESC);
