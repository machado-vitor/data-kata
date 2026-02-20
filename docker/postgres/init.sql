-- PostgreSQL init: schema + seed data for Data Kata
-- Enable logical replication for Debezium CDC
ALTER SYSTEM SET wal_level = logical;

CREATE TABLE salesman (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    region      VARCHAR(100),
    active      BOOLEAN DEFAULT TRUE
);

CREATE TABLE sales (
    id          BIGSERIAL PRIMARY KEY,
    salesman_id BIGINT NOT NULL REFERENCES salesman(id),
    salesman    VARCHAR(255) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    country     VARCHAR(3) NOT NULL DEFAULT 'BR',
    amount      DECIMAL(12,2) NOT NULL,
    product     VARCHAR(255) NOT NULL,
    sale_date   TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create publication for Debezium
CREATE PUBLICATION debezium_pub FOR TABLE sales;

-- Seed salesmen across major Brazilian cities
INSERT INTO salesman (name, city, region, active) VALUES
('Carlos Silva',       'São Paulo',        'Sudeste',  TRUE),
('Ana Oliveira',       'Rio de Janeiro',   'Sudeste',  TRUE),
('Pedro Santos',       'Belo Horizonte',   'Sudeste',  TRUE),
('Maria Souza',        'Curitiba',         'Sul',      TRUE),
('João Costa',         'Porto Alegre',     'Sul',      TRUE),
('Fernanda Lima',      'Salvador',         'Nordeste', TRUE),
('Roberto Almeida',    'Brasília',         'Centro-Oeste', TRUE),
('Juliana Ferreira',   'Florianópolis',    'Sul',      TRUE),
('Lucas Rodrigues',    'Recife',           'Nordeste', TRUE),
('Camila Martins',     'Fortaleza',        'Nordeste', TRUE),
('Gustavo Pereira',    'São Paulo',        'Sudeste',  TRUE),
('Beatriz Nascimento', 'Rio de Janeiro',   'Sudeste',  TRUE),
('Rafael Araújo',      'Belo Horizonte',   'Sudeste',  TRUE),
('Larissa Barbosa',    'Curitiba',         'Sul',      TRUE),
('Thiago Ribeiro',     'Porto Alegre',     'Sul',      FALSE);

-- Seed 55 sales records
INSERT INTO sales (salesman_id, salesman, city, country, amount, product, sale_date, created_at) VALUES
(1,  'Carlos Silva',       'São Paulo',        'BR', 2500.00,  'Laptop',       '2026-02-19 08:15:00', '2026-02-19 08:15:00'),
(1,  'Carlos Silva',       'São Paulo',        'BR', 450.00,   'Monitor',      '2026-02-19 08:30:00', '2026-02-19 08:30:00'),
(2,  'Ana Oliveira',       'Rio de Janeiro',   'BR', 3200.00,  'Laptop',       '2026-02-19 08:45:00', '2026-02-19 08:45:00'),
(2,  'Ana Oliveira',       'Rio de Janeiro',   'BR', 120.00,   'Mouse',        '2026-02-19 09:00:00', '2026-02-19 09:00:00'),
(3,  'Pedro Santos',       'Belo Horizonte',   'BR', 1800.00,  'GPU',          '2026-02-19 09:15:00', '2026-02-19 09:15:00'),
(3,  'Pedro Santos',       'Belo Horizonte',   'BR', 350.00,   'SSD',          '2026-02-19 09:20:00', '2026-02-19 09:20:00'),
(4,  'Maria Souza',        'Curitiba',         'BR', 2800.00,  'Laptop',       '2026-02-19 09:30:00', '2026-02-19 09:30:00'),
(4,  'Maria Souza',        'Curitiba',         'BR', 85.00,    'Keyboard',     '2026-02-19 09:35:00', '2026-02-19 09:35:00'),
(5,  'João Costa',         'Porto Alegre',     'BR', 500.00,   'Monitor',      '2026-02-19 09:45:00', '2026-02-19 09:45:00'),
(5,  'João Costa',         'Porto Alegre',     'BR', 200.00,   'RAM',          '2026-02-19 09:50:00', '2026-02-19 09:50:00'),
(6,  'Fernanda Lima',      'Salvador',         'BR', 1500.00,  'Motherboard',  '2026-02-19 10:00:00', '2026-02-19 10:00:00'),
(6,  'Fernanda Lima',      'Salvador',         'BR', 75.00,    'Headset',      '2026-02-19 10:10:00', '2026-02-19 10:10:00'),
(7,  'Roberto Almeida',    'Brasília',         'BR', 3500.00,  'Laptop',       '2026-02-19 10:15:00', '2026-02-19 10:15:00'),
(7,  'Roberto Almeida',    'Brasília',         'BR', 60.00,    'Webcam',       '2026-02-19 10:20:00', '2026-02-19 10:20:00'),
(8,  'Juliana Ferreira',   'Florianópolis',    'BR', 900.00,   'GPU',          '2026-02-19 10:30:00', '2026-02-19 10:30:00'),
(8,  'Juliana Ferreira',   'Florianópolis',    'BR', 250.00,   'SSD',          '2026-02-19 10:35:00', '2026-02-19 10:35:00'),
(9,  'Lucas Rodrigues',    'Recife',           'BR', 2200.00,  'Laptop',       '2026-02-19 10:45:00', '2026-02-19 10:45:00'),
(9,  'Lucas Rodrigues',    'Recife',           'BR', 180.00,   'RAM',          '2026-02-19 10:50:00', '2026-02-19 10:50:00'),
(10, 'Camila Martins',     'Fortaleza',        'BR', 1200.00,  'Monitor',      '2026-02-19 11:00:00', '2026-02-19 11:00:00'),
(10, 'Camila Martins',     'Fortaleza',        'BR', 95.00,    'Keyboard',     '2026-02-19 11:05:00', '2026-02-19 11:05:00'),
(11, 'Gustavo Pereira',    'São Paulo',        'BR', 4100.00,  'Laptop',       '2026-02-19 11:15:00', '2026-02-19 11:15:00'),
(11, 'Gustavo Pereira',    'São Paulo',        'BR', 700.00,   'GPU',          '2026-02-19 11:20:00', '2026-02-19 11:20:00'),
(12, 'Beatriz Nascimento', 'Rio de Janeiro',   'BR', 1900.00,  'Motherboard',  '2026-02-19 11:30:00', '2026-02-19 11:30:00'),
(12, 'Beatriz Nascimento', 'Rio de Janeiro',   'BR', 300.00,   'SSD',          '2026-02-19 11:35:00', '2026-02-19 11:35:00'),
(13, 'Rafael Araújo',      'Belo Horizonte',   'BR', 2700.00,  'Laptop',       '2026-02-19 11:45:00', '2026-02-19 11:45:00'),
(13, 'Rafael Araújo',      'Belo Horizonte',   'BR', 150.00,   'Headset',      '2026-02-19 11:50:00', '2026-02-19 11:50:00'),
(14, 'Larissa Barbosa',    'Curitiba',         'BR', 550.00,   'Monitor',      '2026-02-19 12:00:00', '2026-02-19 12:00:00'),
(14, 'Larissa Barbosa',    'Curitiba',         'BR', 320.00,   'RAM',          '2026-02-19 12:05:00', '2026-02-19 12:05:00'),
(1,  'Carlos Silva',       'São Paulo',        'BR', 1800.00,  'GPU',          '2026-02-19 12:15:00', '2026-02-19 12:15:00'),
(2,  'Ana Oliveira',       'Rio de Janeiro',   'BR', 2100.00,  'Laptop',       '2026-02-19 12:30:00', '2026-02-19 12:30:00'),
(3,  'Pedro Santos',       'Belo Horizonte',   'BR', 400.00,   'Monitor',      '2026-02-19 12:45:00', '2026-02-19 12:45:00'),
(4,  'Maria Souza',        'Curitiba',         'BR', 90.00,    'Webcam',       '2026-02-19 13:00:00', '2026-02-19 13:00:00'),
(5,  'João Costa',         'Porto Alegre',     'BR', 3600.00,  'Laptop',       '2026-02-19 13:15:00', '2026-02-19 13:15:00'),
(6,  'Fernanda Lima',      'Salvador',         'BR', 280.00,   'SSD',          '2026-02-19 13:30:00', '2026-02-19 13:30:00'),
(7,  'Roberto Almeida',    'Brasília',         'BR', 110.00,   'Mouse',        '2026-02-19 13:45:00', '2026-02-19 13:45:00'),
(8,  'Juliana Ferreira',   'Florianópolis',    'BR', 2400.00,  'Laptop',       '2026-02-19 14:00:00', '2026-02-19 14:00:00'),
(9,  'Lucas Rodrigues',    'Recife',           'BR', 650.00,   'Monitor',      '2026-02-19 14:15:00', '2026-02-19 14:15:00'),
(10, 'Camila Martins',     'Fortaleza',        'BR', 1600.00,  'Motherboard',  '2026-02-19 14:30:00', '2026-02-19 14:30:00'),
(11, 'Gustavo Pereira',    'São Paulo',        'BR', 380.00,   'Keyboard',     '2026-02-19 14:45:00', '2026-02-19 14:45:00'),
(12, 'Beatriz Nascimento', 'Rio de Janeiro',   'BR', 220.00,   'RAM',          '2026-02-19 15:00:00', '2026-02-19 15:00:00'),
(13, 'Rafael Araújo',      'Belo Horizonte',   'BR', 55.00,    'Mouse',        '2026-02-19 15:15:00', '2026-02-19 15:15:00'),
(14, 'Larissa Barbosa',    'Curitiba',         'BR', 1300.00,  'GPU',          '2026-02-19 15:30:00', '2026-02-19 15:30:00'),
(1,  'Carlos Silva',       'São Paulo',        'BR', 195.00,   'Headset',      '2026-02-19 15:45:00', '2026-02-19 15:45:00'),
(5,  'João Costa',         'Porto Alegre',     'BR', 270.00,   'SSD',          '2026-02-19 16:00:00', '2026-02-19 16:00:00'),
(6,  'Fernanda Lima',      'Salvador',         'BR', 2950.00,  'Laptop',       '2026-02-19 16:15:00', '2026-02-19 16:15:00'),
(7,  'Roberto Almeida',    'Brasília',         'BR', 480.00,   'Monitor',      '2026-02-19 16:30:00', '2026-02-19 16:30:00'),
(8,  'Juliana Ferreira',   'Florianópolis',    'BR', 140.00,   'Keyboard',     '2026-02-19 16:45:00', '2026-02-19 16:45:00'),
(9,  'Lucas Rodrigues',    'Recife',           'BR', 1750.00,  'GPU',          '2026-02-19 17:00:00', '2026-02-19 17:00:00'),
(10, 'Camila Martins',     'Fortaleza',        'BR', 310.00,   'RAM',          '2026-02-19 17:15:00', '2026-02-19 17:15:00'),
(11, 'Gustavo Pereira',    'São Paulo',        'BR', 2300.00,  'Motherboard',  '2026-02-19 17:30:00', '2026-02-19 17:30:00'),
(12, 'Beatriz Nascimento', 'Rio de Janeiro',   'BR', 45.00,    'Webcam',       '2026-02-19 17:45:00', '2026-02-19 17:45:00'),
(3,  'Pedro Santos',       'Belo Horizonte',   'BR', 3100.00,  'Laptop',       '2026-02-19 18:00:00', '2026-02-19 18:00:00'),
(4,  'Maria Souza',        'Curitiba',         'BR', 165.00,   'Headset',      '2026-02-19 18:15:00', '2026-02-19 18:15:00'),
(5,  'João Costa',         'Porto Alegre',     'BR', 430.00,   'Keyboard',     '2026-02-19 18:30:00', '2026-02-19 18:30:00'),
(6,  'Fernanda Lima',      'Salvador',         'BR', 780.00,   'Monitor',      '2026-02-19 18:45:00', '2026-02-19 18:45:00'),
(10, 'Camila Martins',     'Fortaleza',        'BR', 2600.00,  'Laptop',       '2026-02-19 19:00:00', '2026-02-19 19:00:00');
