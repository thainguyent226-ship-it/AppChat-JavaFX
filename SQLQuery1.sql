IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND type in (N'U'))
BEGIN
    CREATE TABLE users (
        username NVARCHAR(50) PRIMARY KEY,
        password NVARCHAR(50) NOT NULL,
        full_name NVARCHAR(100) NULL,
        dob NVARCHAR(50) NULL,
        university NVARCHAR(150) NULL,
        email NVARCHAR(100) NULL,
        phone NVARCHAR(20) NULL
    );
END;

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[chat_groups]') AND type in (N'U'))
BEGIN
    CREATE TABLE chat_groups (
        group_name NVARCHAR(100) PRIMARY KEY,
        creator NVARCHAR(50) NULL
    );
END;

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[group_members]') AND type in (N'U'))
BEGIN
    CREATE TABLE group_members (
        group_name NVARCHAR(100),
        username NVARCHAR(50),
        PRIMARY KEY (group_name, username)
    );
END;

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[messages]') AND type in (N'U'))
BEGIN
    CREATE TABLE messages (
        id INT IDENTITY(1,1) PRIMARY KEY,
        sender NVARCHAR(50) NOT NULL,
        receiver NVARCHAR(100) NULL,     -- co gia tri neu la tin nhan rieng (1-1)
        group_name NVARCHAR(100) NULL,   -- co gia tri neu la tin nhan nhom
        content NVARCHAR(MAX) NOT NULL,
        msg_type NVARCHAR(20) NOT NULL DEFAULT 'TEXT',
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END;

-- Them cot khoa tai khoan (block user) vao bang users neu chua co
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[users]') AND name = 'is_blocked')
BEGIN
    ALTER TABLE users ADD is_blocked BIT NOT NULL DEFAULT 0;
END;

-- Bang quan ly ban be (loi moi ket ban + trang thai)
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[friends]') AND type in (N'U'))
BEGIN
    CREATE TABLE friends (
        id INT IDENTITY(1,1) PRIMARY KEY,
        user1 NVARCHAR(50) NOT NULL,   -- nguoi gui loi moi
        user2 NVARCHAR(50) NOT NULL,   -- nguoi nhan loi moi
        status NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING hoac ACCEPTED
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
END;

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[messages]') AND name = 'status')
BEGIN
    ALTER TABLE messages ADD status NVARCHAR(20) NOT NULL DEFAULT 'SENT'; -- SENT / DELIVERED / SEEN
END;

SELECT * FROM users;
SELECT * FROM chat_groups;
SELECT * FROM group_members;
SELECT * FROM messages;
SELECT * FROM friends;