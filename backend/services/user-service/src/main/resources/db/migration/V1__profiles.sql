CREATE TABLE profiles (
  user_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin PRIMARY KEY,
  display_name VARCHAR(100) NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT positive_version CHECK (version > 0)
);
