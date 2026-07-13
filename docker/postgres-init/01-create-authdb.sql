-- deliverydb is auto-created via POSTGRES_DB; auth-service and chat-service each need
-- their own dedicated database (chat-service owns its persistence independently, same
-- isolation pattern as auth-service - see chat-service's application.yml).
CREATE DATABASE authdb;
CREATE DATABASE chatdb;