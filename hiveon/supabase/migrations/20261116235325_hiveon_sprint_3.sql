create table hiveon.expedientes_sanitarios (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    remitente_id bigint not null references hiveon.usuarios (id),
    descripcion text not null check (btrim(descripcion) <> ''),
    remitido_en timestamptz not null default current_timestamp,
    decision text check (decision in ('VALIDADO', 'RECHAZADO')),
    inspector_id bigint references hiveon.usuarios (id),
    resuelto_en timestamptz,
    observaciones_revision text,
    check (
        (decision is null and inspector_id is null and resuelto_en is null
            and observaciones_revision is null)
        or (decision is not null and inspector_id is not null and resuelto_en is not null)
    ),
    check (resuelto_en >= remitido_en)
);

create index expedientes_apiario_idx on hiveon.expedientes_sanitarios (apiario_id, remitido_en desc);
create index expedientes_pendientes_idx
    on hiveon.expedientes_sanitarios (remitido_en)
    where decision is null;

create table hiveon.cosechas (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    colmena_id bigint,
    autor_id bigint not null references hiveon.usuarios (id),
    fecha date not null,
    cantidad_kg numeric(12, 3) not null
        check (cantidad_kg > 0 and cantidad_kg <> 'NaN'::numeric),
    unique (apiario_id, id),
    foreign key (apiario_id, colmena_id) references hiveon.colmenas (apiario_id, id)
);

create index cosechas_apiario_fecha_idx on hiveon.cosechas (apiario_id, fecha desc);

create table hiveon.lotes_miel (
    id bigint generated always as identity primary key,
    apiario_id bigint not null references hiveon.apiarios (id),
    codigo text not null check (codigo = btrim(codigo) and codigo <> ''),
    creado_en timestamptz not null default current_timestamp,
    publicado_en timestamptz,
    unique (codigo),
    unique (apiario_id, id),
    check (publicado_en >= creado_en)
);

create index lotes_publicados_idx on hiveon.lotes_miel (publicado_en desc)
    where publicado_en is not null;

-- Each harvest goes entirely into at most one lot. Both must share an apiary.
create table hiveon.lote_cosechas (
    apiario_id bigint not null,
    lote_id bigint not null,
    cosecha_id bigint primary key,
    foreign key (apiario_id, lote_id) references hiveon.lotes_miel (apiario_id, id),
    foreign key (apiario_id, cosecha_id) references hiveon.cosechas (apiario_id, id)
);

create index lote_cosechas_lote_idx on hiveon.lote_cosechas (apiario_id, lote_id);

create table hiveon.eventos_auditoria (
    id bigint generated always as identity primary key,
    actor_id bigint references hiveon.usuarios (id),
    accion text not null check (btrim(accion) <> ''),
    recurso text check (btrim(recurso) <> ''),
    recurso_id bigint,
    resultado text not null check (btrim(resultado) <> ''),
    ocurrido_en timestamptz not null default current_timestamp,
    check (recurso_id is null or recurso is not null)
);

create index eventos_auditoria_fecha_idx on hiveon.eventos_auditoria (ocurrido_en desc, id desc);
