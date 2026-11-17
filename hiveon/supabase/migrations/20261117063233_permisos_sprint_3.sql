-- Grant the dedicated runtime account the columns required by Sprint 3.
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'hiveon_vercel_app') then
        grant select on hiveon.roles to hiveon_vercel_app;
        grant insert (rol_codigo, nombre, correo, contrasena_hash),
            update (rol_codigo, nombre, correo, contrasena_hash, habilitado)
            on hiveon.usuarios to hiveon_vercel_app;
        grant insert (nombre), update (nombre, habilitado)
            on hiveon.tipos_colmena to hiveon_vercel_app;
        grant select, insert (apiario_id, autor_id, colmena_id, fecha, cantidad_kg)
            on hiveon.cosechas to hiveon_vercel_app;
        grant select, insert (apiario_id, codigo), update (codigo, publicado_en)
            on hiveon.lotes_miel to hiveon_vercel_app;
        grant select, insert, delete on hiveon.lote_cosechas to hiveon_vercel_app;
        grant select, insert (apiario_id, remitente_id, descripcion),
            update (decision, inspector_id, resuelto_en, observaciones_revision)
            on hiveon.expedientes_sanitarios to hiveon_vercel_app;
        grant select, insert (actor_id, accion, recurso, recurso_id, resultado)
            on hiveon.eventos_auditoria to hiveon_vercel_app;
        grant usage on sequence hiveon.usuarios_id_seq, hiveon.tipos_colmena_id_seq,
            hiveon.cosechas_id_seq, hiveon.lotes_miel_id_seq, hiveon.expedientes_sanitarios_id_seq,
            hiveon.eventos_auditoria_id_seq to hiveon_vercel_app;
    end if;
end;
$$;
