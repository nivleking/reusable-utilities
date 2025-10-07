#!/bin/bash
set -e

echo "Waiting for PostgreSQL to start..."
until docker exec postgres pg_isready -U nivleking -d utilities > /dev/null 2>&1; do
  sleep 1
done
echo "PostgreSQL is ready!"

echo "Initializing database..."
docker exec -i postgres psql -U nivleking -d utilities < src/main/resources/db/init/init-database.sql
echo "Database initialization complete!"