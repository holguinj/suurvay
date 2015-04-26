CREATE TABLE statuses (
    id bigint NOT NULL,
    status jsonb NOT NULL,
    user_id bigint NOT NULL
);
--;;

COMMENT ON COLUMN statuses.id IS 'The status ID, not an auto-incrementing PG id.';
--;;

COMMENT ON COLUMN statuses.status IS 'The Twitter status object';
--;;

COMMENT ON COLUMN statuses.user_id IS 'The Twitter user ID';
--;;

ALTER TABLE ONLY statuses
    ADD CONSTRAINT statuses_pkey PRIMARY KEY (id);
--;;

CREATE TABLE twitter_users (
    id bigint NOT NULL,
    user_object jsonb NOT NULL
);
--;;

ALTER TABLE ONLY twitter_users
    ADD CONSTRAINT twitter_users_pkey PRIMARY KEY (id);
