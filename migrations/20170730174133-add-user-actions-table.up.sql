-- migration to be applied

CREATE TABLE app_instances(
  app_instance_id         text      NOT NULL,
  device_manufacturer     text      NOT NULL,
  device_brand            text      NOT NULL,
  device_model            text      NOT NULL,
  time_first_run_occurred timestamp NOT NULL,
  time_first_run_sent     timestamp NOT NULL,
  time_first_run_received timestamp NOT NULL,
  CONSTRAINT app_instances_pkey PRIMARY KEY (app_instance_id)
);



CREATE TABLE events(
  event_type                text      NOT NULL,
  payload                   jsonb,
  provenance                jsonb     NOT NULL,
  app_instance_id           text      NOT NULL REFERENCES app_instances ON DELETE CASCADE,
  app_version               text      NOT NULL,
  os_type                   text      NOT NULL,
  os_version_release        text      NOT NULL,
  os_version_security_patch text,
  os_sdk                    numeric,
  time_occurred             timestamp NOT NULL,
  time_sent                 timestamp NOT NULL,
  time_received             timestamp NOT NULL,
  PRIMARY KEY (time_occurred, app_instance_id)
);



DO $$
BEGIN
PERFORM create_hypertable('events', 'time_occurred');
END$$;
