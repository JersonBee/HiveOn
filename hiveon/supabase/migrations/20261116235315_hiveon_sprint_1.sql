-- Keep hiveon outside the schemas exposed by the Supabase Data API.
create schema hiveon;
revoke all on schema hiveon from public;

create table hiveon.roles (
    codigo text primary key check (codigo = upper(codigo) and btrim(codigo) <> ''),
    nombre text not null check (btrim(nombre) <> '')
);

insert into hiveon.roles (codigo, nombre) values
    ('APICULTOR', 'Apicultor'),
    ('OPERARIO', 'Operario de campo'),
    ('TECNICO', 'Técnico apícola'),
    ('INSPECTOR', 'Inspector sanitario simulado'),
    ('COMPRADOR', 'Comprador o acopiador'),
    ('ADMINISTRADOR', 'Administrador');

create table hiveon.usuarios (
    id bigint generated always as identity primary key,
    rol_codigo text not null references hiveon.roles (codigo),
    nombre text not null check (btrim(nombre) <> ''),
    correo text not null check (correo = btrim(correo) and correo <> ''),
    contrasena_hash text not null check (btrim(contrasena_hash) <> ''),
    habilitado boolean not null default true,
    creado_en timestamptz not null default current_timestamp
);

create unique index usuarios_correo_unico on hiveon.usuarios (lower(correo));

create table hiveon.apiarios (
    id bigint generated always as identity primary key,
    propietario_id bigint not null references hiveon.usuarios (id),
    nombre text not null check (btrim(nombre) <> ''),
    departamento text not null check (btrim(departamento) <> ''),
    municipio text not null check (btrim(municipio) <> ''),
    vereda text check (btrim(vereda) <> ''),
    creado_en timestamptz not null default current_timestamp,
    archivado_en timestamptz,
    check (archivado_en >= creado_en)
);

create index apiarios_propietario_idx on hiveon.apiarios (propietario_id);
