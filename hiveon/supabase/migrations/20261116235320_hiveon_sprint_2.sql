create table hiveon.tipos_colmena (
    id bigint generated always as identity primary key,
    nombre text not null check (nombre = btrim(nombre) and nombre <> ''),
    habilitado boolean not null default true
);

create unique index tipos_colmena_nombre_unico on hiveon.tipos_colmena (lower(nombre));

create table hiveon.colmenas (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    tipo_id bigint references hiveon.tipos_colmena (id),
    codigo text not null check (codigo = btrim(codigo) and codigo <> ''),
    fecha_instalacion date,
    creado_en timestamptz not null default current_timestamp,
    archivado_en timestamptz,
    unique (apiario_id, codigo),
    unique (apiario_id, id),
    check (archivado_en >= creado_en)
);

create table hiveon.vinculaciones_apiario (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    usuario_id bigint not null references hiveon.usuarios (id),
    funcion text not null check (funcion in ('OPERARIO', 'TECNICO')),
    vinculada_en timestamptz not null default current_timestamp,
    retirada_en timestamptz,
    unique (apiario_id, id, funcion),
    check (retirada_en >= vinculada_en)
);

create unique index vinculaciones_vigentes_unicas
    on hiveon.vinculaciones_apiario (apiario_id, usuario_id)
    where retirada_en is null;
create index vinculaciones_usuario_vigente_idx
    on hiveon.vinculaciones_apiario (usuario_id, apiario_id)
    where retirada_en is null;

create table hiveon.inspecciones (
    id bigint generated always as identity primary key,
    colmena_id bigint not null references hiveon.colmenas (id),
    autor_id bigint not null references hiveon.usuarios (id),
    fecha date not null,
    fuerza_colonia text,
    estado_reina text,
    plagas_observadas text,
    alimentacion text,
    observaciones text not null check (btrim(observaciones) <> '')
);

create index inspecciones_colmena_fecha_idx on hiveon.inspecciones (colmena_id, fecha desc);

create table hiveon.tareas (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    vinculacion_id bigint not null,
    funcion_asignada text generated always as ('OPERARIO'::text) stored,
    creador_id bigint not null references hiveon.usuarios (id),
    descripcion text not null check (btrim(descripcion) <> ''),
    vencimiento date not null,
    estado text not null default 'PENDIENTE'
        check (estado in ('PENDIENTE', 'EN_CURSO', 'COMPLETADA')),
    creada_en timestamptz not null default current_timestamp,
    foreign key (apiario_id, vinculacion_id, funcion_asignada)
        references hiveon.vinculaciones_apiario (apiario_id, id, funcion)
);

create index tareas_apiario_estado_idx on hiveon.tareas (apiario_id, estado, vencimiento);
create index tareas_vinculacion_idx on hiveon.tareas (vinculacion_id);

create table hiveon.tratamientos (
    id bigint generated always as identity primary key,
    colmena_id bigint not null references hiveon.colmenas (id),
    autor_id bigint not null references hiveon.usuarios (id),
    fecha date not null,
    descripcion text not null check (btrim(descripcion) <> '')
);

create index tratamientos_colmena_fecha_idx on hiveon.tratamientos (colmena_id, fecha desc);

create table hiveon.recomendaciones (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    colmena_id bigint,
    autor_id bigint not null references hiveon.usuarios (id),
    contenido text not null check (btrim(contenido) <> ''),
    creada_en timestamptz not null default current_timestamp,
    foreign key (apiario_id, colmena_id) references hiveon.colmenas (apiario_id, id)
);

create index recomendaciones_apiario_fecha_idx on hiveon.recomendaciones (apiario_id, creada_en desc);
