-- The database checks event dates on insert and update.
-- Date-only values use the calendar day in America/Bogota.
create function hiveon.validar_fechas_registro()
returns trigger language plpgsql set search_path = pg_catalog as $$
declare
    v_registro jsonb := to_jsonb(new);
    v_ahora timestamptz := clock_timestamp();
    v_hoy date := (v_ahora at time zone 'America/Bogota')::date;
    v_campo text;
    v_valor text;
    v_fecha date;
    v_instante timestamptz;
begin
    foreach v_campo in array tg_argv loop
        -- Date-only arguments are prefixed with date:; the others are timestamps.
        if left(v_campo, 5) = 'date:' then
            v_campo := substr(v_campo, 6);
            v_valor := v_registro ->> v_campo;
            v_fecha := v_valor::date;
            if v_fecha is null or (isfinite(v_fecha) and v_fecha <= v_hoy) then
                continue;
            end if;
        else
            v_valor := v_registro ->> v_campo;
            v_instante := v_valor::timestamptz;
            if v_instante is null or (isfinite(v_instante) and v_instante <= v_ahora) then
                continue;
            end if;
        end if;

        raise exception using errcode = '23514',
            message = format('%I.%I debe ser una fecha finita que no esté en el futuro.',
                tg_table_name, v_campo);
    end loop;
    return new;
end;
$$;

alter table hiveon.tareas add constraint tareas_vencimiento_valido
    check (isfinite(vencimiento)
        and vencimiento >= (creada_en at time zone 'America/Bogota')::date);

-- Check existing rows before enabling the same rules for writes.
do $$
begin
    if exists (
        select 1 from hiveon.apiarios
        where not isfinite(creado_en) or creado_en > clock_timestamp()
           or not isfinite(archivado_en) or archivado_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.apiarios.';
    end if;

    if exists (
        select 1 from hiveon.colmenas
        where not isfinite(fecha_instalacion) or fecha_instalacion > (clock_timestamp() at time zone 'America/Bogota')::date
           or not isfinite(creado_en) or creado_en > clock_timestamp()
           or not isfinite(archivado_en) or archivado_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.colmenas.';
    end if;

    if exists (
        select 1 from hiveon.cosechas
        where not isfinite(fecha) or fecha > (clock_timestamp() at time zone 'America/Bogota')::date
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.cosechas.';
    end if;

    if exists (
        select 1 from hiveon.eventos_auditoria
        where not isfinite(ocurrido_en) or ocurrido_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.eventos_auditoria.';
    end if;

    if exists (
        select 1 from hiveon.expedientes_sanitarios
        where not isfinite(remitido_en) or remitido_en > clock_timestamp()
           or not isfinite(resuelto_en) or resuelto_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.expedientes_sanitarios.';
    end if;

    if exists (
        select 1 from hiveon.inspecciones
        where not isfinite(fecha) or fecha > (clock_timestamp() at time zone 'America/Bogota')::date
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.inspecciones.';
    end if;

    if exists (
        select 1 from hiveon.lotes_miel
        where not isfinite(creado_en) or creado_en > clock_timestamp()
           or not isfinite(publicado_en) or publicado_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.lotes_miel.';
    end if;

    if exists (
        select 1 from hiveon.recomendaciones
        where not isfinite(creada_en) or creada_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.recomendaciones.';
    end if;

    if exists (
        select 1 from hiveon.tareas
        where not isfinite(creada_en) or creada_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.tareas.';
    end if;

    if exists (
        select 1 from hiveon.tratamientos
        where not isfinite(fecha) or fecha > (clock_timestamp() at time zone 'America/Bogota')::date
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.tratamientos.';
    end if;

    if exists (
        select 1 from hiveon.usuarios
        where not isfinite(creado_en) or creado_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.usuarios.';
    end if;

    if exists (
        select 1 from hiveon.vinculaciones_apiario
        where not isfinite(vinculada_en) or vinculada_en > clock_timestamp()
           or not isfinite(retirada_en) or retirada_en > clock_timestamp()
    ) then
        raise exception using errcode = '23514',
            message = 'Hay fechas inválidas en hiveon.vinculaciones_apiario.';
    end if;
end;
$$;

create trigger apiarios_validar_fechas
    before insert or update on hiveon.apiarios
    for each row execute function hiveon.validar_fechas_registro('creado_en', 'archivado_en');

create trigger colmenas_validar_fechas
    before insert or update on hiveon.colmenas
    for each row execute function hiveon.validar_fechas_registro('date:fecha_instalacion', 'creado_en', 'archivado_en');

create trigger cosechas_validar_fechas
    before insert or update on hiveon.cosechas
    for each row execute function hiveon.validar_fechas_registro('date:fecha');

create trigger eventos_auditoria_validar_fechas
    before insert or update on hiveon.eventos_auditoria
    for each row execute function hiveon.validar_fechas_registro('ocurrido_en');

create trigger expedientes_sanitarios_validar_fechas
    before insert or update on hiveon.expedientes_sanitarios
    for each row execute function hiveon.validar_fechas_registro('remitido_en', 'resuelto_en');

create trigger inspecciones_validar_fechas
    before insert or update on hiveon.inspecciones
    for each row execute function hiveon.validar_fechas_registro('date:fecha');

create trigger lotes_miel_validar_fechas
    before insert or update on hiveon.lotes_miel
    for each row execute function hiveon.validar_fechas_registro('creado_en', 'publicado_en');

create trigger recomendaciones_validar_fechas
    before insert or update on hiveon.recomendaciones
    for each row execute function hiveon.validar_fechas_registro('creada_en');

create trigger tareas_validar_fechas
    before insert or update on hiveon.tareas
    for each row execute function hiveon.validar_fechas_registro('creada_en');

create trigger tratamientos_validar_fechas
    before insert or update on hiveon.tratamientos
    for each row execute function hiveon.validar_fechas_registro('date:fecha');

create trigger usuarios_validar_fechas
    before insert or update on hiveon.usuarios
    for each row execute function hiveon.validar_fechas_registro('creado_en');

create trigger vinculaciones_apiario_validar_fechas
    before insert or update on hiveon.vinculaciones_apiario
    for each row execute function hiveon.validar_fechas_registro('vinculada_en', 'retirada_en');
