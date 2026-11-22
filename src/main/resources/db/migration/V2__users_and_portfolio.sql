-- Item #12: contas, sessões e carteira multi-ativo.
-- Valores monetários e quantidades em NUMERIC (nunca em ponto flutuante): ADR 002.

CREATE TABLE users (
    id               UUID PRIMARY KEY,
    email            VARCHAR(254) NOT NULL,
    display_name     VARCHAR(80)  NOT NULL,
    avatar_url       VARCHAR(500),
    auth_provider    VARCHAR(16)  NOT NULL,
    password_hash    VARCHAR(100),
    google_subject   VARCHAR(255),
    investor_profile VARCHAR(16),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_provider_ck CHECK (auth_provider IN ('PASSWORD', 'GOOGLE', 'DEMO')),
    CONSTRAINT users_profile_ck CHECK (investor_profile IS NULL OR investor_profile IN ('CONSERVATIVE', 'MODERATE', 'BOLD', 'AGGRESSIVE')),
    -- conta com senha tem hash, conta Google tem o sub do Google, e nenhuma outra tem credencial
    CONSTRAINT users_credential_ck CHECK (
        (auth_provider = 'PASSWORD') = (password_hash IS NOT NULL)
        AND (auth_provider = 'GOOGLE') = (google_subject IS NOT NULL))
);

CREATE UNIQUE INDEX users_email_uq ON users (email);
CREATE UNIQUE INDEX users_google_subject_uq ON users (google_subject) WHERE google_subject IS NOT NULL;
CREATE INDEX users_demo_created_idx ON users (created_at) WHERE auth_provider = 'DEMO';

-- Só o hash SHA-256 do refresh token é guardado. "used" marca o token já trocado: reapresentá-lo revoga a família inteira.
CREATE TABLE refresh_tokens (
    token_hash CHAR(64)     PRIMARY KEY,
    family_id  VARCHAR(40)  NOT NULL,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);

-- Uma posição por usuário e ativo. asset_key é o símbolo do universo ou "T:TIPO:rotulo" para ativos sem cotação.
-- version é o contador do bloqueio otimista: a atualização só vale se a versão lida ainda for a do banco.
CREATE TABLE positions (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_key     VARCHAR(120) NOT NULL,
    symbol        VARCHAR(32),
    asset_type    VARCHAR(32)  NOT NULL,
    label         VARCHAR(120) NOT NULL,
    quantity      NUMERIC(28, 10) NOT NULL,
    average_price NUMERIC(28, 10) NOT NULL,
    currency      CHAR(3)      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT positions_quantity_ck CHECK (quantity > 0),
    CONSTRAINT positions_price_ck CHECK (average_price > 0),
    CONSTRAINT positions_currency_ck CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT positions_user_asset_uq UNIQUE (user_id, asset_key)
);

CREATE INDEX positions_user_idx ON positions (user_id, created_at);
