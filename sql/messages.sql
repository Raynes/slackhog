DROP TABLE messages;
CREATE TABLE messages (
  raw_ts    VARCHAR,
  ts        TIMESTAMP,
  ts_suffix INT,
  text      TEXT,
  id        VARCHAR,
  channel   VARCHAR
);