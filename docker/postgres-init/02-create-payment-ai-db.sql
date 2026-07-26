-- ai-service now persists frozen estimates (see Estimate entity) and payment-service
-- needs its own database, same isolation pattern as auth-service/chat-service.
CREATE DATABASE aidb;
CREATE DATABASE paymentdb;
