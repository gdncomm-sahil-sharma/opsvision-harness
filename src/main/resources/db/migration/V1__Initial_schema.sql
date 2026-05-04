-- Initial schema for OpsVision Investigation Harness

-- Create session table
CREATE TABLE session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255),
    initial_query TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for session table
CREATE INDEX idx_session_user_id ON session(user_id);
CREATE INDEX idx_session_status ON session(status);
CREATE INDEX idx_session_created_at ON session(created_at);

-- Create session_metadata table for additional metadata
CREATE TABLE session_metadata (
    session_id UUID NOT NULL REFERENCES session(id) ON DELETE CASCADE,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value TEXT,
    PRIMARY KEY (session_id, metadata_key)
);

-- Create conversation table
CREATE TABLE conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES session(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    query TEXT NOT NULL,
    response TEXT,
    context_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(session_id, sequence_number)
);

-- Create indexes for conversation table
CREATE INDEX idx_conversation_session_id ON conversation(session_id);
CREATE INDEX idx_conversation_sequence ON conversation(session_id, sequence_number);

-- Create tool_execution table
CREATE TABLE tool_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    result JSONB,
    execution_time_ms INTEGER,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for tool_execution table
CREATE INDEX idx_tool_execution_conversation_id ON tool_execution(conversation_id);
CREATE INDEX idx_tool_execution_tool_name ON tool_execution(tool_name);
CREATE INDEX idx_tool_execution_status ON tool_execution(status);