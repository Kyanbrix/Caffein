
CREATE TABLE IF NOT EXISTS user_messages(

        userid bigint PRIMARY KEY,
        total_messages int DEFAULT 0,
        last_message timestamptz

)