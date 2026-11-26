CREATE TABLE operador_departamento (
    operador_id     UUID NOT NULL REFERENCES operador (id) ON DELETE CASCADE,
    departamento_id UUID NOT NULL REFERENCES departamento (id) ON DELETE CASCADE,
    PRIMARY KEY (operador_id, departamento_id)
);
CREATE INDEX idx_operador_departamento_dep ON operador_departamento (departamento_id);
