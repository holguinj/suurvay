CREATE OR REPLACE FUNCTION get_entity_id(entity jsonb) RETURNS bigint
  AS $BODY$
  BEGIN
    RETURN entity->'id';
  END;
  $BODY$
    LANGUAGE plpgsql IMMUTABLE;
--;;

CREATE OR REPLACE FUNCTION get_entity_id(entity json) RETURNS bigint
  AS $BODY$
  BEGIN
    RETURN entity->'id';
  END;
  $BODY$
    LANGUAGE plpgsql IMMUTABLE;
--;;

CREATE OR REPLACE FUNCTION get_status_user_id(status jsonb) RETURNS bigint
  AS $BODY$
  BEGIN
    RETURN status#>'{user,id}';
  END;
  $BODY$
    LANGUAGE plpgsql IMMUTABLE;
--;;

CREATE OR REPLACE FUNCTION get_status_user_id(status json) RETURNS bigint
  AS $BODY$
  BEGIN
    RETURN status#>'{user,id}';
  END;
  $BODY$
    LANGUAGE plpgsql IMMUTABLE;
--;;

CREATE OR REPLACE FUNCTION insert_statuses(statuses json) RETURNS void
  AS $BODY$
  DECLARE
    this_user bigint;
    id bigint;
    status record;
    this_status json;
  BEGIN
    this_user = statuses->0#>'{user,id}';
    FOR status IN SELECT * FROM json_array_elements(statuses) LOOP
      this_status = to_json(status."value");
      id = this_status->'id';
      BEGIN
        INSERT INTO statuses VALUES (id, this_status::jsonb, this_user);
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

CREATE OR REPLACE FUNCTION insert_user(this_user json) RETURNS void
  AS $BODY$
  DECLARE
    this_id bigint;
  BEGIN
    this_id = this_user->'id';
    BEGIN
      INSERT INTO twitter_users VALUES(this_id, this_user::jsonb);
    EXCEPTION
      WHEN unique_violation THEN
        NULL;
    END;
    RETURN;
  END;
  $BODY$
    LANGUAGE plpgsql VOLATILE;
--;;
