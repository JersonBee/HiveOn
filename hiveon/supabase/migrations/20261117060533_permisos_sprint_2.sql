-- Extend the dedicated Vercel JDBC role to the Sprint 2 operations.
-- The role is provisioned separately so local databases need no deployment credentials.
do $$
begin
    if exists(select 1 from pg_roles where rolname = 'hiveon_vercel_app') then
        grant select (nombre) on hiveon.usuarios to hiveon_vercel_app;
        grant update (nombre, municipio_codigo, vereda, archivado_en) on hiveon.apiarios to hiveon_vercel_app;
        grant select, update (id) on hiveon.tipos_colmena to hiveon_vercel_app;
        grant select, insert (apiario_id, tipo_id, codigo, fecha_instalacion),
            update (tipo_id, codigo, fecha_instalacion, archivado_en) on hiveon.colmenas to hiveon_vercel_app;
        grant select, insert (apiario_id, usuario_id, funcion), update (retirada_en)
            on hiveon.vinculaciones_apiario to hiveon_vercel_app;
        grant select, insert (colmena_id, autor_id, fecha, observaciones) on hiveon.inspecciones to hiveon_vercel_app;
        grant select, insert (apiario_id, vinculacion_id, creador_id, descripcion, vencimiento), update (estado)
            on hiveon.tareas to hiveon_vercel_app;
        grant select, insert (colmena_id, autor_id, fecha, descripcion) on hiveon.tratamientos to hiveon_vercel_app;
        grant select, insert (apiario_id, colmena_id, autor_id, contenido) on hiveon.recomendaciones to hiveon_vercel_app;
        grant usage on sequence hiveon.colmenas_id_seq, hiveon.vinculaciones_apiario_id_seq,
            hiveon.inspecciones_id_seq, hiveon.tareas_id_seq, hiveon.tratamientos_id_seq,
            hiveon.recomendaciones_id_seq to hiveon_vercel_app;
    end if;
end;
$$;
