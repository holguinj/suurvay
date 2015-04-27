CREATE TABLE "public"."follows" (
    "id" bigint NOT NULL,
    "follows" bigint NOT NULL,
    UNIQUE (id, follows)
);
--;;

CREATE OR REPLACE FUNCTION add_followers(user_id bigint, followers bigint[]) RETURNS void
  AS $BODY$
  DECLARE
    follower bigint;
  BEGIN
    FOREACH follower IN ARRAY followers LOOP
      BEGIN
        INSERT INTO follows VALUES (follower, user_id);
      EXCEPTION
        WHEN unique_violation THEN
          NULL;
      END;
    END LOOP;
    RETURN;
  END;
  $BODY$
    LANGUAGE plpgsql VOLATILE;
--;;

CREATE OR REPLACE FUNCTION add_friends(user_id bigint, friends bigint[]) RETURNS void
  AS $BODY$
  DECLARE
    friend bigint;
  BEGIN
    FOREACH friend IN ARRAY friends LOOP
      BEGIN
        INSERT INTO follows VALUES (user_id, friend);
      EXCEPTION
        WHEN unique_violation THEN
          NULL;
      END;
    END LOOP;
    RETURN;
  END;
  $BODY$
    LANGUAGE plpgsql VOLATILE;
--;;
