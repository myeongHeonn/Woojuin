ALTER TABLE chat_account_connections
    ADD COLUMN oauth_access_token text,
    ADD COLUMN oauth_refresh_token text,
    ADD COLUMN oauth_expires_at timestamp(6) with time zone;

CREATE UNIQUE INDEX uk_chat_account_connections_platform_woojuin_user
    ON chat_account_connections (platform, user_id);
